package br.com.tecnodreamer.petconnect.produto.application;

import br.com.tecnodreamer.petconnect.compra.domain.Compra;
import br.com.tecnodreamer.petconnect.fornecedor.domain.Fornecedor;
import br.com.tecnodreamer.petconnect.produto.domain.LoteEstoque;
import br.com.tecnodreamer.petconnect.produto.domain.Produto;
import br.com.tecnodreamer.petconnect.produto.infrastructure.LoteEstoqueRepository;
import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;
import br.com.tecnodreamer.petconnect.shared.error.ConflictException;
import br.com.tecnodreamer.petconnect.usuario.domain.Usuario;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Identidade e política dos lotes (etapa 02). NÃO movimenta saldo: quem altera saldo é o
 * {@link EstoqueService} (etapa 03), com o produto travado. Nada aqui está ligado a rota, venda ou compra,
 * e a flag {@code petconnect.estoque.lotes.habilitado} é verificada por quem expõe o recurso.
 */
@Service
@EnableConfigurationProperties(LotesProperties.class)
public class LoteEstoqueService {

    private final LoteEstoqueRepository lotes;
    private final LotesProperties propriedades;
    private final Clock clock;

    public LoteEstoqueService(LoteEstoqueRepository lotes, LotesProperties propriedades, Clock clock) {
        this.lotes = lotes;
        this.propriedades = propriedades;
        this.clock = clock;
    }

    /**
     * Devolve o lote do produto com este código, criando-o se não existir (D3). Receber de novo o mesmo código
     * reaproveita o lote (o custo e a origem da primeira entrada ficam como estão, D16); validade diferente sob o
     * mesmo código é erro de digitação e dá 409. Exige transação aberta e o produto já travado pelo chamador (D15).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public LoteEstoque obterOuCriar(Produto produto, String codigoLote, LocalDate validade, BigDecimal custoUnitario,
            LocalDate dataRecebimento, Compra compra, Fornecedor fornecedor, Usuario usuario) {
        String codigo = normalizar(codigoLote);
        if (validade == null && !produto.isPermiteSemValidade()) {
            throw new CampoInvalidoException("validade", "Este produto exige validade em todos os lotes.");
        }
        var existente = lotes.findByProdutoIdAndCodigoLote(produto.getId(), codigo);
        if (existente.isPresent()) {
            LoteEstoque lote = existente.get();
            if (!Objects.equals(lote.getValidade(), validade)) {
                throw new ConflictException("O lote " + codigo + " já existe com outra validade (" + lote.getValidade() + ").");
            }
            return lote;
        }
        return lotes.save(new LoteEstoque(produto, codigo, validade, custoUnitario, dataRecebimento, compra, fornecedor, usuario));
    }

    /** Margem de bloqueio em dias: a do produto, se definida; senão a global (D4). */
    public int margemDeBloqueio(Produto produto) {
        return propriedades.margemDoProduto(produto.getDiasBloqueioValidade());
    }

    /** Lotes que o FEFO pode usar hoje, na ordem de consumo (D6). */
    @Transactional(readOnly = true)
    public List<LoteEstoque> vendaveis(Produto produto) {
        LocalDate limite = hoje().plusDays(margemDeBloqueio(produto));
        return lotes.vendaveisEmOrdemFefo(produto.getId(), limite);
    }

    public LocalDate hoje() {
        return LocalDate.now(clock);
    }

    public boolean habilitado() {
        return propriedades.habilitado();
    }

    private static String normalizar(String codigo) {
        try {
            return LoteEstoque.normalizarCodigo(codigo);
        } catch (IllegalArgumentException e) {
            throw new CampoInvalidoException("codigoLote", e.getMessage());
        }
    }
}
