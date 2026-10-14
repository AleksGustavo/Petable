package br.com.tecnodreamer.petconnect.compra.application;

import br.com.tecnodreamer.petconnect.compra.domain.Compra;
import br.com.tecnodreamer.petconnect.compra.domain.ItemCompra;
import br.com.tecnodreamer.petconnect.compra.domain.StatusCompra;
import br.com.tecnodreamer.petconnect.compra.infrastructure.CompraRepository;
import br.com.tecnodreamer.petconnect.compra.infrastructure.ItemCompraLoteRepository;
import br.com.tecnodreamer.petconnect.compra.web.CompraRequest;
import br.com.tecnodreamer.petconnect.compra.web.CompraResponse;
import br.com.tecnodreamer.petconnect.financeiro.application.FinanceiroService;
import br.com.tecnodreamer.petconnect.financeiro.domain.ContaPagar;
import br.com.tecnodreamer.petconnect.financeiro.web.ContaPagarResponse;
import br.com.tecnodreamer.petconnect.fornecedor.domain.Fornecedor;
import br.com.tecnodreamer.petconnect.fornecedor.infrastructure.FornecedorRepository;
import br.com.tecnodreamer.petconnect.funcionario.domain.Funcionario;
import br.com.tecnodreamer.petconnect.funcionario.infrastructure.FuncionarioRepository;
import br.com.tecnodreamer.petconnect.compra.web.RecebimentoRequest;
import br.com.tecnodreamer.petconnect.produto.application.EstoqueLoteService;
import br.com.tecnodreamer.petconnect.produto.application.EstoqueService;
import br.com.tecnodreamer.petconnect.produto.application.LoteInformado;
import br.com.tecnodreamer.petconnect.produto.domain.Produto;
import br.com.tecnodreamer.petconnect.produto.infrastructure.ProdutoRepository;
import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;
import br.com.tecnodreamer.petconnect.shared.error.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CompraService {

    static final String ENTIDADE = "compra";
    /** Limite da coluna valor_total DECIMAL(10,2). */
    static final BigDecimal TOTAL_MAXIMO = new BigDecimal("99999999.99");

    private final CompraRepository compras;
    private final FornecedorRepository fornecedores;
    private final FuncionarioRepository funcionarios;
    private final ProdutoRepository produtos;
    private final EstoqueService estoque;
    private final EstoqueLoteService estoqueLotes;
    private final ItemCompraLoteRepository itensCompraLote;
    private final AuditoriaService auditoria;
    private final FinanceiroService financeiro;
    private final Clock clock;

    public CompraService(CompraRepository compras, FornecedorRepository fornecedores, FuncionarioRepository funcionarios,
            ProdutoRepository produtos, EstoqueService estoque, EstoqueLoteService estoqueLotes, ItemCompraLoteRepository itensCompraLote, AuditoriaService auditoria,
            FinanceiroService financeiro, Clock clock) {
        this.compras = compras;
        this.fornecedores = fornecedores;
        this.funcionarios = funcionarios;
        this.produtos = produtos;
        this.estoque = estoque;
        this.estoqueLotes = estoqueLotes;
        this.itensCompraLote = itensCompraLote;
        this.auditoria = auditoria;
        this.financeiro = financeiro;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<CompraResponse> listar(StatusCompra status, Long fornecedorId, LocalDate inicio, LocalDate fim, Pageable pageable) {
        if (inicio != null && fim != null && fim.isBefore(inicio)) {
            throw new CampoInvalidoException("dataFim", "A data final não pode ser anterior à inicial.");
        }
        return compras.buscar(status, fornecedorId, inicio, fim, pageable).map(CompraResponse::resumo);
    }

    @Transactional(readOnly = true)
    public CompraResponse buscar(Long id) {
        Compra compra = compras.findWithItensById(id).orElseThrow(() -> new NotFoundException("Compra", id));
        CompraResponse r = CompraResponse.of(compra).comLotes(lotesRecebidos(compra));
        return r.comConta(ContaPagarResponse.Resumo.of(financeiro.contaDaCompra(id).orElse(null)));
    }

    /** Lotes recebidos em cada item (uma consulta só; vazio em compra sem produto controlado ou ainda em PEDIDO). */
    private Map<Long, List<CompraResponse.LoteRecebido>> lotesRecebidos(Compra compra) {
        List<Long> itemIds = compra.getItens().stream().map(ItemCompra::getId).toList();
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        return itensCompraLote.dosItens(itemIds).stream().collect(Collectors.groupingBy(l -> l.getItemCompra().getId(),
                Collectors.mapping(l -> new CompraResponse.LoteRecebido(l.getLote() == null ? null : l.getLote().getId(),
                        l.getCodigoLote(), l.getValidade(), l.getQuantidade()), Collectors.toList())));
    }

    /** Cria o PEDIDO. Não mexe no estoque (regra 22); o total vem da soma dos itens. */
    @Transactional
    public CompraResponse criar(CompraRequest req) {
        Fornecedor fornecedor = fornecedores.findById(req.fornecedorId()).filter(Fornecedor::isAtivo)
                .orElseThrow(() -> new CampoInvalidoException("fornecedorId", "Fornecedor não encontrado ou inativo."));
        Funcionario funcionario = null;
        if (req.funcionarioId() != null) {
            funcionario = funcionarios.findById(req.funcionarioId()).filter(Funcionario::isDisponivel)
                    .orElseThrow(() -> new CampoInvalidoException("funcionarioId", "Funcionário não encontrado ou inativo."));
        }
        if (req.dataCompra().isAfter(LocalDate.now(clock))) {
            throw new CampoInvalidoException("dataCompra", "A data da compra não pode ser futura.");
        }
        if (req.itens() == null || req.itens().isEmpty()) {
            throw new CampoInvalidoException("itens", "Adicione pelo menos um item.");
        }
        Set<Long> vistos = new HashSet<>();
        for (CompraRequest.Item item : req.itens()) {
            if (!vistos.add(item.produtoId())) {
                throw new CampoInvalidoException("itens", "O mesmo produto aparece mais de uma vez na compra.");
            }
        }
        Map<Long, Produto> porId = produtos.findAllById(vistos).stream()
                .collect(Collectors.toMap(Produto::getId, Function.identity()));

        Compra compra = new Compra(fornecedor, funcionario, req.dataCompra(), req.formaPagamento().trim(),
                limpo(req.observacoes()));
        for (CompraRequest.Item item : req.itens()) {
            Produto p = porId.get(item.produtoId());
            if (p == null || !p.isAtivo()) {
                throw new CampoInvalidoException("itens", "Produto " + item.produtoId() + " não encontrado ou inativo.");
            }
            compra.adicionarItem(p, item.quantidade(), item.precoCustoUnitario());
        }
        if (compra.getValorTotal().compareTo(TOTAL_MAXIMO) > 0) {
            throw new CampoInvalidoException("itens", "O total da compra excede o limite permitido.");
        }
        Compra salva = compras.saveAndFlush(compra);
        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("fornecedorId", fornecedor.getId());
        detalhes.put("valorTotal", salva.getValorTotal());
        detalhes.put("itens", salva.getItens().size());
        auditoria.registrar("COMPRA_CRIADA", ENTIDADE, salva.getId(), detalhes);
        return CompraResponse.of(salva);
    }

    /**
     * Recebimento atômico (DA-07): trava a compra, valida PEDIDO, trava os produtos
     * em ordem crescente de id, dá entrada com custo médio e marca RECEBIDO. Qualquer
     * falha desfaz tudo (mesma transação).
     */
    @Transactional
    public CompraResponse receber(Long id, LocalDate dataEntrega, LocalDate dataVencimento, Long usuarioId) {
        return receber(id, dataEntrega, dataVencimento, null, usuarioId);
    }

    /**
     * Recebimento com lotes (D17): para cada item de produto controlado, {@code lotes} traz os lotes recebidos (soma = quantidade
     * do item). Produto sem controle segue o fluxo de antes e não pode vir com lotes. A repetição da chamada cai em 409 pela
     * transição PEDIDO → RECEBIDO, sem nova entrada.
     */
    @Transactional
    public CompraResponse receber(Long id, LocalDate dataEntrega, LocalDate dataVencimento,
            List<RecebimentoRequest.LotesDoItem> lotesPorProduto, Long usuarioId) {
        Compra compra = compras.travar(id).orElseThrow(() -> new NotFoundException("Compra", id));
        compra.getStatus().exigirTransicaoPara(StatusCompra.RECEBIDO);
        if (dataEntrega.isAfter(LocalDate.now(clock))) {
            throw new CampoInvalidoException("dataEntrega", "A data de entrega não pode ser futura.");
        }
        if (dataEntrega.isBefore(compra.getDataCompra())) {
            throw new CampoInvalidoException("dataEntrega", "A data de entrega não pode ser anterior à data da compra.");
        }
        // DA-10: vencimento estruturado entre a entrega e +365 dias.
        if (dataVencimento == null) {
            throw new CampoInvalidoException("dataVencimento", "Informe o vencimento da conta.");
        }
        if (dataVencimento.isBefore(dataEntrega) || dataVencimento.isAfter(dataEntrega.plusDays(365))) {
            throw new CampoInvalidoException("dataVencimento", "O vencimento deve ficar entre a entrega e 365 dias depois dela.");
        }
        List<ItemCompra> itens = compra.getItens();
        List<Long> ids = itens.stream().map(i -> i.getProduto().getId()).sorted().toList();
        Map<Long, Produto> travados = produtos.travarEmOrdem(ids).stream()
                .collect(Collectors.toMap(Produto::getId, Function.identity()));
        Map<Long, List<LoteInformado>> lotesDe = lotesPorProduto(lotesPorProduto, itens, travados);
        for (ItemCompra item : itens.stream().sorted((a, b) -> a.getProduto().getId().compareTo(b.getProduto().getId())).toList()) {
            Produto travado = travados.get(item.getProduto().getId());
            if (travado.isControlaLote()) {
                estoqueLotes.receberItem(travado, item, lotesDe.get(travado.getId()), dataEntrega, compra, usuarioId);
            } else {
                estoque.registrarEntradaCompra(travado, item.getQuantidade(), item.getPrecoCustoUnitario(), compra, usuarioId);
            }
        }
        compra.receber(dataEntrega);
        Compra salva = compras.saveAndFlush(compra);
        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("valorTotal", salva.getValorTotal());
        detalhes.put("itens", itens.size());
        detalhes.put("dataEntrega", dataEntrega.toString());
        auditoria.registrar("COMPRA_RECEBIDA", ENTIDADE, id, detalhes);
        // DA-09: conta a pagar na mesma transação; nenhuma saída financeira até o pagamento.
        ContaPagar conta = financeiro.criarContaPagar(salva, dataVencimento);
        return CompraResponse.of(salva).comConta(ContaPagarResponse.Resumo.of(conta));
    }

    /** Lotes informados por produto; produto sem controle de lote com lotes informados é erro do chamador. */
    private static Map<Long, List<LoteInformado>> lotesPorProduto(List<RecebimentoRequest.LotesDoItem> informados,
            List<ItemCompra> itens, Map<Long, Produto> travados) {
        Map<Long, List<LoteInformado>> porProduto = new java.util.HashMap<>();
        if (informados != null) {
            for (RecebimentoRequest.LotesDoItem l : informados) {
                if (porProduto.put(l.produtoId(), l.lotes()) != null) {
                    throw new CampoInvalidoException("lotes", "O produto " + l.produtoId() + " aparece mais de uma vez nos lotes.");
                }
            }
        }
        Set<Long> daCompra = itens.stream().map(i -> i.getProduto().getId()).collect(Collectors.toSet());
        for (Long produtoId : porProduto.keySet()) {
            Produto p = travados.get(produtoId);
            if (!daCompra.contains(produtoId)) {
                throw new CampoInvalidoException("lotes", "O produto " + produtoId + " não faz parte desta compra.");
            }
            if (!p.isControlaLote()) {
                throw new CampoInvalidoException("lotes", "O produto " + p.getNome() + " não controla lote.");
            }
        }
        return porProduto;
    }

    /** Só PEDIDO; não mexe no estoque; o motivo vai para a auditoria e para as observações. */
    @Transactional
    public CompraResponse cancelar(Long id, String motivo) {
        Compra compra = compras.travar(id).orElseThrow(() -> new NotFoundException("Compra", id));
        compra.getStatus().exigirTransicaoPara(StatusCompra.CANCELADO);
        String texto = motivo == null ? "" : motivo.trim();
        if (texto.length() < 5 || texto.length() > 500) {
            throw new CampoInvalidoException("motivo", "O motivo deve ter entre 5 e 500 caracteres.");
        }
        compra.cancelar(texto);
        Compra salva = compras.saveAndFlush(compra);
        auditoria.registrar("COMPRA_CANCELADA", ENTIDADE, id, Map.of("motivo", texto));
        return CompraResponse.of(salva);
    }

    private static String limpo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
