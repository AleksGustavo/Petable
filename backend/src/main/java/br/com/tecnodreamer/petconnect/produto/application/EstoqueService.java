package br.com.tecnodreamer.petconnect.produto.application;

import br.com.tecnodreamer.petconnect.compra.domain.Compra;
import br.com.tecnodreamer.petconnect.produto.domain.EstoqueInsuficienteException;
import br.com.tecnodreamer.petconnect.produto.domain.MovimentacaoEstoque;
import br.com.tecnodreamer.petconnect.produto.domain.Produto;
import br.com.tecnodreamer.petconnect.produto.domain.TipoMovimentacao;
import br.com.tecnodreamer.petconnect.produto.infrastructure.MovimentacaoEstoqueRepository;
import br.com.tecnodreamer.petconnect.produto.infrastructure.ProdutoRepository;
import br.com.tecnodreamer.petconnect.produto.web.MovimentacaoResponse;
import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;
import br.com.tecnodreamer.petconnect.shared.error.ConflictException;
import br.com.tecnodreamer.petconnect.shared.error.NotFoundException;
import br.com.tecnodreamer.petconnect.usuario.domain.Usuario;
import br.com.tecnodreamer.petconnect.usuario.infrastructure.UsuarioRepository;
import br.com.tecnodreamer.petconnect.venda.domain.Venda;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serviço central de estoque: o ÚNICO lugar que muda {@code quantidade_estoque}.
 * Toda mudança acontece com o produto travado (FOR UPDATE) e gera uma
 * movimentação imutável: ajuste, entrada de compra, saída e estorno de venda.
 */
@Service
public class EstoqueService {

    static final String ESTOQUE_NEGATIVO = "O ajuste resultaria em estoque negativo.";

    private final ProdutoRepository produtos;
    private final MovimentacaoEstoqueRepository movimentacoes;
    private final UsuarioRepository usuarios;
    private final CustoMedioService custoMedio;
    private final AuditoriaService auditoria;

    public EstoqueService(ProdutoRepository produtos, MovimentacaoEstoqueRepository movimentacoes, UsuarioRepository usuarios,
            CustoMedioService custoMedio, AuditoriaService auditoria) {
        this.produtos = produtos;
        this.movimentacoes = movimentacoes;
        this.usuarios = usuarios;
        this.custoMedio = custoMedio;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Page<MovimentacaoResponse> historico(Long produtoId, TipoMovimentacao tipo, Pageable pageable) {
        if (!produtos.existsById(produtoId)) {
            throw new NotFoundException("Produto", produtoId);
        }
        return movimentacoes.historico(produtoId, tipo, pageable).map(MovimentacaoResponse::of);
    }

    /** Ajuste manual (DA-09): delta ≠ 0, motivo obrigatório, nunca negativo, não mexe no custo. Vale para produto inativo. */
    @Transactional
    public MovimentacaoResponse ajustar(Long produtoId, int delta, String motivo, Long usuarioId) {
        if (delta == 0) {
            throw new CampoInvalidoException("quantidade", "Informe uma quantidade diferente de zero.");
        }
        String texto = motivo == null ? "" : motivo.trim();
        if (texto.length() < 5 || texto.length() > 500) {
            throw new CampoInvalidoException("motivo", "O motivo deve ter entre 5 e 500 caracteres.");
        }
        Produto p = produtos.travar(produtoId).orElseThrow(() -> new NotFoundException("Produto", produtoId));
        if (p.isControlaLote()) {
            throw new CampoInvalidoException("loteId", "Este produto controla lote: informe o lote do ajuste.");
        }
        if (p.getQuantidadeEstoque() + (long) delta < 0) {
            throw new ConflictException(ESTOQUE_NEGATIVO);
        }
        MovimentacaoEstoque m = movimentar(p, delta, TipoMovimentacao.AJUSTE, null, usuario(usuarioId), texto);
        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("produtoId", produtoId);
        detalhes.put("quantidade", delta);
        detalhes.put("saldoAnterior", m.getSaldoAnterior());
        detalhes.put("saldoResultante", m.getSaldoResultante());
        detalhes.put("motivo", texto);
        auditoria.registrar("ESTOQUE_AJUSTADO", "produto", produtoId, detalhes);
        return MovimentacaoResponse.of(m);
    }

    /**
     * Entrada por recebimento de compra. O produto já deve vir travado pelo chamador
     * (a compra trava todos em ordem de id). Atualiza o custo médio.
     */
    @Transactional
    public MovimentacaoEstoque registrarEntradaCompra(Produto produtoTravado, int quantidade, BigDecimal custoUnitario,
            Compra compra, Long usuarioId) {
        recusarSeControlaLote(produtoTravado);
        BigDecimal novoCusto = custoMedio.calcular(produtoTravado.getQuantidadeEstoque(), produtoTravado.getPrecoCusto(),
                quantidade, custoUnitario);
        MovimentacaoEstoque m = movimentar(produtoTravado, quantidade, TipoMovimentacao.ENTRADA_COMPRA, compra,
                usuario(usuarioId), null);
        produtoTravado.atualizarCusto(novoCusto);
        return m;
    }

    /**
     * Saída por venda (DA-04): produto já travado pelo chamador (em ordem de id);
     * grava SAIDA_VENDA com quantidade NEGATIVA e o id da venda. Não mexe no custo.
     */
    @Transactional
    public MovimentacaoEstoque registrarSaidaVenda(Produto produtoTravado, int quantidade, Venda venda, Long usuarioId) {
        recusarSeControlaLote(produtoTravado);
        exigirPositiva(quantidade);
        if (produtoTravado.getQuantidadeEstoque() < quantidade) {
            throw new EstoqueInsuficienteException(produtoTravado, quantidade);
        }
        return movimentarVenda(produtoTravado, -quantidade, TipoMovimentacao.SAIDA_VENDA, venda, usuarioId);
    }

    /** Estorno do cancelamento (DA-07): devolve a quantidade, inclusive a produto desativado. Não mexe no custo. */
    @Transactional
    public MovimentacaoEstoque registrarEstornoVenda(Produto produtoTravado, int quantidade, Venda venda, Long usuarioId) {
        recusarSeControlaLote(produtoTravado);
        exigirPositiva(quantidade);
        return movimentarVenda(produtoTravado, quantidade, TipoMovimentacao.ESTORNO_VENDA, venda, usuarioId);
    }

    private MovimentacaoEstoque movimentarVenda(Produto p, int delta, TipoMovimentacao tipo, Venda venda, Long usuarioId) {
        Usuario usuario = usuario(usuarioId);
        int anterior = p.movimentar(delta);
        produtos.save(p);
        return movimentacoes.save(new MovimentacaoEstoque(p, tipo, delta, anterior, p.getQuantidadeEstoque(), venda.getId(),
                usuario));
    }

    /** Produto com lote só se movimenta pelo {@link EstoqueLoteService}: não existe caminho que ignore o lote. */
    private static void recusarSeControlaLote(Produto p) {
        if (p.isControlaLote()) {
            throw new ConflictException("O produto " + p.getNome() + " controla lote; a movimentação precisa informar o lote.");
        }
    }

    private static void exigirPositiva(int quantidade) {
        if (quantidade <= 0) {
            throw new IllegalArgumentException("A quantidade da venda deve ser positiva.");
        }
    }

    private MovimentacaoEstoque movimentar(Produto p, int delta, TipoMovimentacao tipo, Compra compra, Usuario usuario,
            String motivo) {
        int anterior = p.movimentar(delta);
        produtos.save(p);
        return movimentacoes.save(new MovimentacaoEstoque(p, tipo, delta, anterior, p.getQuantidadeEstoque(), compra,
                usuario, motivo));
    }

    private Usuario usuario(Long usuarioId) {
        return usuarios.findById(usuarioId).orElseThrow(() -> new NotFoundException("Usuário", usuarioId));
    }
}
