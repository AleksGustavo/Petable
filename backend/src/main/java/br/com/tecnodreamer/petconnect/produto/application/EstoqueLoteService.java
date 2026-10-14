package br.com.tecnodreamer.petconnect.produto.application;

import br.com.tecnodreamer.petconnect.compra.domain.Compra;
import br.com.tecnodreamer.petconnect.compra.domain.ItemCompra;
import br.com.tecnodreamer.petconnect.compra.domain.ItemCompraLote;
import br.com.tecnodreamer.petconnect.compra.infrastructure.ItemCompraLoteRepository;
import br.com.tecnodreamer.petconnect.produto.application.AlocadorFefo.Alocacao;
import br.com.tecnodreamer.petconnect.produto.domain.EstoqueInsuficienteException;
import br.com.tecnodreamer.petconnect.produto.domain.LoteEstoque;
import br.com.tecnodreamer.petconnect.produto.domain.MovimentacaoEstoque;
import br.com.tecnodreamer.petconnect.produto.domain.Produto;
import br.com.tecnodreamer.petconnect.produto.domain.StatusLote;
import br.com.tecnodreamer.petconnect.produto.domain.TipoMovimentacao;
import br.com.tecnodreamer.petconnect.produto.infrastructure.LoteEstoqueRepository;
import br.com.tecnodreamer.petconnect.produto.infrastructure.MovimentacaoEstoqueRepository;
import br.com.tecnodreamer.petconnect.produto.infrastructure.ProdutoRepository;
import br.com.tecnodreamer.petconnect.produto.web.LoteResponse;
import br.com.tecnodreamer.petconnect.produto.web.MovimentacaoResponse;
import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;
import br.com.tecnodreamer.petconnect.shared.error.ConflictException;
import br.com.tecnodreamer.petconnect.shared.error.NotFoundException;
import br.com.tecnodreamer.petconnect.usuario.domain.Usuario;
import br.com.tecnodreamer.petconnect.usuario.infrastructure.UsuarioRepository;
import br.com.tecnodreamer.petconnect.venda.domain.DestinoEstoque;
import br.com.tecnodreamer.petconnect.venda.domain.ItemVenda;
import br.com.tecnodreamer.petconnect.venda.domain.ItemVendaLote;
import br.com.tecnodreamer.petconnect.venda.domain.Venda;
import br.com.tecnodreamer.petconnect.venda.infrastructure.ItemVendaLoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Toda movimentação de estoque de produto COM controle de lote (D1): entrada de compra, baixa FEFO da venda,
 * estorno ao lote original, ajuste, perda, vencimento e regularização do saldo legado. Complementa o
 * {@link EstoqueService}, que continua sendo o único caminho dos produtos sem lote (e recusa os controlados).
 *
 * <p>Regras que valem para tudo aqui: o produto vem travado (FOR UPDATE) e é o mutex dos seus lotes (D15); cada
 * mudança grava o lote, o total do produto e um movimento imutável com os saldos dos dois (I2, I6); saldo negativo
 * é recusado antes de gravar. Quem altera saldo fora de {@code produto.application} viola a regra de arquitetura
 * verificada em teste.
 */
@Service
public class EstoqueLoteService {

    static final String LOTES_DESLIGADOS = "O controle de lote não está habilitado nesta instalação.";
    static final int PRAZO_CANCELAMENTO_DIAS = 7;

    private final ProdutoRepository produtos;
    private final LoteEstoqueRepository lotes;
    private final LoteEstoqueService loteService;
    private final MovimentacaoEstoqueRepository movimentacoes;
    private final UsuarioRepository usuarios;
    private final CustoMedioService custoMedio;
    private final AuditoriaService auditoria;
    private final ItemVendaLoteRepository itensVendaLote;
    private final ItemCompraLoteRepository itensCompraLote;

    public EstoqueLoteService(ProdutoRepository produtos, LoteEstoqueRepository lotes, LoteEstoqueService loteService,
            MovimentacaoEstoqueRepository movimentacoes, UsuarioRepository usuarios, CustoMedioService custoMedio,
            AuditoriaService auditoria, ItemVendaLoteRepository itensVendaLote, ItemCompraLoteRepository itensCompraLote) {
        this.produtos = produtos;
        this.lotes = lotes;
        this.loteService = loteService;
        this.movimentacoes = movimentacoes;
        this.usuarios = usuarios;
        this.custoMedio = custoMedio;
        this.auditoria = auditoria;
        this.itensVendaLote = itensVendaLote;
        this.itensCompraLote = itensCompraLote;
    }

    // ------------------------------------------------------------------ consultas

    @Transactional(readOnly = true)
    public List<LoteResponse> listar(Long produtoId) {
        Produto p = produtos.findById(produtoId).orElseThrow(() -> new NotFoundException("Produto", produtoId));
        LocalDate hoje = loteService.hoje();
        int margem = loteService.margemDeBloqueio(p);
        return lotes.findByProdutoIdOrderByIdAsc(produtoId).stream().map(l -> LoteResponse.of(l, hoje, margem)).toList();
    }

    /** Saldo que o FEFO pode vender hoje (soma dos lotes vendáveis). Leitura comum, para a prévia. */
    @Transactional(readOnly = true)
    public int disponivelParaVenda(Produto p) {
        return loteService.vendaveis(p).stream().mapToInt(LoteEstoque::getSaldo).sum();
    }

    /** O mesmo saldo, lido com trava, para decidir a venda. Chamar com o produto já travado. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int disponivelParaBaixa(Produto p) {
        return loteService.vendaveisTravados(p).stream().mapToInt(LoteEstoque::getSaldo).sum();
    }

    /** Prévia da alocação FEFO, sem gravar: a mesma lógica da venda (D8). Vazio quando não há saldo vendável suficiente. */
    @Transactional(readOnly = true)
    public Optional<List<Alocacao>> previaAlocacao(Produto p, int quantidade) {
        return AlocadorFefo.alocar(loteService.vendaveis(p), quantidade);
    }

    @Transactional(readOnly = true)
    public boolean temAlocacao(ItemVenda item) {
        return itensVendaLote.existsByItemVendaId(item.getId());
    }

    // ------------------------------------------------------------ entrada de compra

    /**
     * Recebimento de um item de produto controlado (D17): os lotes informados somam exatamente a quantidade do item.
     * Cada lote vira um movimento ENTRADA_COMPRA; o mesmo código reaproveita o lote existente (D3). O custo médio do
     * produto é calculado uma vez, com a quantidade total do item.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void receberItem(Produto p, ItemCompra item, List<LoteInformado> informados, LocalDate dataEntrega, Compra compra,
            Long usuarioId) {
        List<LoteInformado> infos = informados == null ? List.of() : informados;
        validarLotes(p, infos, item.getQuantidade(), true);
        Usuario usuario = usuario(usuarioId);
        BigDecimal novoCusto = custoMedio.calcular(p.getQuantidadeEstoque(), p.getPrecoCusto(), item.getQuantidade(),
                item.getPrecoCustoUnitario());
        for (LoteInformado info : infos) {
            LoteEstoque lote = obterOuCriarAuditado(p, info, item.getPrecoCustoUnitario(), dataEntrega, compra, usuario);
            mover(p, lote, TipoMovimentacao.ENTRADA_COMPRA, info.quantidade(), compra, null, usuario, null, null, true);
            ItemCompraLote recebido = new ItemCompraLote(item, lote.getCodigoLote(), lote.getValidade(), info.quantidade());
            recebido.vincularLote(lote);
            itensCompraLote.save(recebido);
        }
        p.atualizarCusto(novoCusto);
    }

    // --------------------------------------------------------------------- venda

    /**
     * Baixa FEFO de um item de venda: distribui a quantidade pelos lotes vendáveis e grava, por lote, a alocação do item
     * e um movimento SAIDA_VENDA. Sem saldo vendável suficiente: 409 e nada gravado (a transação inteira é desfeita).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<ItemVendaLote> baixarVenda(Produto p, ItemVenda item, Venda venda, Long usuarioId) {
        List<LoteEstoque> vendaveis = loteService.vendaveisTravados(p);
        int solicitado = item.getQuantidade();
        List<Alocacao> alocacoes = AlocadorFefo.alocar(vendaveis, solicitado).orElseThrow(
                () -> new EstoqueInsuficienteException(p, solicitado, vendaveis.stream().mapToInt(LoteEstoque::getSaldo).sum()));
        Usuario usuario = usuario(usuarioId);
        List<ItemVendaLote> gravadas = new ArrayList<>();
        for (Alocacao a : alocacoes) {
            mover(p, a.lote(), TipoMovimentacao.SAIDA_VENDA, -a.quantidade(), null, venda.getId(), usuario, null, null, true);
            gravadas.add(itensVendaLote.save(new ItemVendaLote(item, a.lote(), a.quantidade())));
        }
        return gravadas;
    }

    /**
     * Cancelamento (D9): devolve exatamente o que {@link #baixarVenda} tirou, lote a lote. RETORNAR recompõe o lote;
     * DESCARTAR grava ESTORNO_VENDA (+n) e PERDA (-n) no mesmo lote e na mesma operação, então o saldo vendável não sobe.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void estornarVenda(Produto p, ItemVenda item, Venda venda, DestinoEstoque destino, Long usuarioId) {
        Usuario usuario = usuario(usuarioId);
        String operacao = UUID.randomUUID().toString();
        for (ItemVendaLote alocado : itensVendaLote.findByItemVendaIdOrderByIdAsc(item.getId())) {
            LoteEstoque lote = alocado.getLote();
            int quantidade = alocado.getQuantidade();
            mover(p, lote, TipoMovimentacao.ESTORNO_VENDA, quantidade, null, venda.getId(), usuario, null, operacao, true);
            if (destino == DestinoEstoque.DESCARTAR) {
                mover(p, lote, TipoMovimentacao.PERDA, -quantidade, null, venda.getId(), usuario,
                        "Descarte no cancelamento da venda " + venda.getId(), operacao, true);
            }
        }
    }

    // ------------------------------------------------------------- consumo clínico

    /** A aplicação clínica deste produto baixa estoque: controle de lote ligado na instalação e no produto. */
    public boolean consumoClinicoAtivo(Produto produto) {
        return produto != null && loteService.habilitado() && produto.isControlaLote();
    }

    /**
     * Tira UMA unidade do lote que o FEFO escolheria (D6): é a dose de vacina aplicada. O produto é travado aqui, então a
     * regra de concorrência é a das vendas. Sem lote vendável (vencido, bloqueado, sem saldo ou dentro da margem), 409 e nada
     * é gravado. O movimento e a auditoria não guardam dado do paciente nem do tutor.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public LoteEstoque baixarConsumoClinico(Long produtoId, String motivo, Long usuarioId) {
        Produto p = produtos.travar(produtoId).orElseThrow(() -> new NotFoundException("Produto", produtoId));
        if (!p.isControlaLote()) {
            throw new ConflictException("O produto " + p.getNome() + " não controla lote.");
        }
        List<LoteEstoque> vendaveis = loteService.vendaveisTravados(p);
        if (vendaveis.isEmpty()) {
            throw new ConflictException("Não há lote disponível de " + p.getNome() + " para aplicação (vencido, bloqueado ou sem saldo).");
        }
        LoteEstoque lote = vendaveis.get(0);
        MovimentacaoEstoque m = mover(p, lote, TipoMovimentacao.CONSUMO_CLINICO, -1, null, null, usuario(usuarioId), motivo, null, true);
        auditoria.registrar("ESTOQUE_CONSUMO_CLINICO", "produto", produtoId, detalhesMovimento(m, motivo));
        return lote;
    }

    // ----------------------------------------------------- ajuste, perda, vencimento

    /**
     * Ajuste manual de produto controlado. Positivo: lote existente ({@code loteId}) ou identificável ({@code codigoLote}
     * + validade). Negativo: {@code loteId} obrigatório e o saldo do lote não pode ficar negativo.
     */
    @Transactional
    public MovimentacaoResponse ajustar(Long produtoId, int delta, String motivo, Long loteId, String codigoLote,
            LocalDate validade, Long usuarioId) {
        exigirHabilitado();
        if (delta == 0) {
            throw new CampoInvalidoException("quantidade", "Informe uma quantidade diferente de zero.");
        }
        String texto = motivoValido(motivo);
        Produto p = produtoControlado(produtoId);
        Usuario usuario = usuario(usuarioId);
        LoteEstoque lote;
        if (loteId != null) {
            lote = loteDoProduto(p, loteId);
        } else if (delta > 0 && codigoLote != null && !codigoLote.isBlank()) {
            lote = obterOuCriarAuditado(p, new LoteInformado(codigoLote, validade, delta), p.getPrecoCusto(), loteService.hoje(),
                    null, usuario);
        } else {
            throw new CampoInvalidoException("loteId", delta > 0
                    ? "Informe o lote do ajuste (loteId, ou código e validade de um lote novo)."
                    : "Informe o lote do qual sairá a quantidade (loteId).");
        }
        if (lote.getSaldo() + (long) delta < 0) {
            throw new ConflictException("O ajuste excede o saldo do lote " + lote.getCodigoLote() + " (" + lote.getSaldo() + ").");
        }
        MovimentacaoEstoque m = mover(p, lote, TipoMovimentacao.AJUSTE, delta, null, null, usuario, texto, null, true);
        auditoria.registrar("ESTOQUE_AJUSTADO", "produto", produtoId, detalhesMovimento(m, texto));
        return MovimentacaoResponse.of(m);
    }

    /** Perda de um lote (dano, quebra, extravio): baixa PERDA com motivo. */
    @Transactional
    public MovimentacaoResponse registrarPerda(Long produtoId, Long loteId, int quantidade, String motivo, Long usuarioId) {
        return baixar(produtoId, loteId, quantidade, motivo, usuarioId, TipoMovimentacao.PERDA, "ESTOQUE_PERDA");
    }

    /** Baixa de lote VENCIDO. Lote ainda dentro da validade não pode ser baixado por este caminho. */
    @Transactional
    public MovimentacaoResponse registrarVencimento(Long produtoId, Long loteId, int quantidade, String motivo, Long usuarioId) {
        return baixar(produtoId, loteId, quantidade, motivo, usuarioId, TipoMovimentacao.VENCIMENTO, "ESTOQUE_VENCIMENTO");
    }

    private MovimentacaoResponse baixar(Long produtoId, Long loteId, int quantidade, String motivo, Long usuarioId,
            TipoMovimentacao tipo, String evento) {
        exigirHabilitado();
        if (quantidade <= 0) {
            throw new CampoInvalidoException("quantidade", "A quantidade deve ser positiva.");
        }
        String texto = motivoValido(motivo);
        Produto p = produtoControlado(produtoId);
        LoteEstoque lote = loteDoProduto(p, loteId);
        if (tipo == TipoMovimentacao.VENCIMENTO && !lote.isVencidoEm(loteService.hoje())) {
            throw new ConflictException("O lote " + lote.getCodigoLote() + " ainda está dentro da validade.");
        }
        if (lote.getSaldo() < quantidade) {
            throw new ConflictException("A baixa excede o saldo do lote " + lote.getCodigoLote() + " (" + lote.getSaldo() + ").");
        }
        MovimentacaoEstoque m = mover(p, lote, tipo, -quantidade, null, null, usuario(usuarioId), texto, null, true);
        auditoria.registrar(evento, "produto", produtoId, detalhesMovimento(m, texto));
        return MovimentacaoResponse.of(m);
    }

    // ---------------------------------------------------- ligar/desligar o controle

    /**
     * Liga o controle de lote do produto, distribuindo o saldo existente nos lotes informados (D11). A soma precisa ser
     * exatamente o saldo atual: nenhum lote nem validade é inventado. Grava um REGULARIZACAO_LOTE por lote (o total do
     * produto não muda) e o evento ESTOQUE_REGULARIZADO, numa transação com o produto travado.
     */
    @Transactional
    public List<LoteResponse> ativarControle(Long produtoId, boolean permiteSemValidade, Integer diasBloqueio,
            List<LoteInformado> informados, Long usuarioId) {
        exigirHabilitado();
        Produto p = produtos.travar(produtoId).orElseThrow(() -> new NotFoundException("Produto", produtoId));
        if (p.isControlaLote()) {
            throw new ConflictException("O produto já controla lote.");
        }
        try {
            p.definirPoliticaDeLote(permiteSemValidade, diasBloqueio);
        } catch (IllegalArgumentException e) {
            throw new CampoInvalidoException("diasBloqueioValidade", e.getMessage());
        }
        // D13: vender sem lote e cancelar depois não teria lote de origem.
        long pendentes = itensVendaLote.contarItensCancelaveisSemLote(produtoId,
                loteService.agora().minusDays(PRAZO_CANCELAMENTO_DIAS));
        if (pendentes > 0) {
            throw new ConflictException("O produto tem " + pendentes + " item(ns) de venda ainda cancelável(is) sem lote. "
                    + "Aguarde o prazo de cancelamento (" + PRAZO_CANCELAMENTO_DIAS + " dias) ou cancele essas vendas antes.");
        }
        List<LoteInformado> infos = informados == null ? List.of() : informados;
        int saldo = p.getQuantidadeEstoque();
        int soma = infos.stream().mapToInt(LoteInformado::quantidade).sum();
        if (soma != saldo) {
            throw new ConflictException("Os lotes informados somam " + soma + ", mas o saldo atual é " + saldo
                    + ". A distribuição precisa ser exata.");
        }
        validarLotes(p, infos, saldo, false);
        Usuario usuario = usuario(usuarioId);
        String operacao = UUID.randomUUID().toString();
        for (LoteInformado info : infos) {
            LoteEstoque lote = obterOuCriarAuditado(p, info, p.getPrecoCusto(), loteService.hoje(), null, usuario);
            mover(p, lote, TipoMovimentacao.REGULARIZACAO_LOTE, info.quantidade(), null, null, usuario,
                    "Regularização do saldo legado em lotes", operacao, false);
        }
        p.ativarControleLote();
        produtos.save(p);
        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("produtoId", produtoId);
        detalhes.put("lotes", infos.size());
        detalhes.put("quantidade", saldo);
        detalhes.put("permiteSemValidade", permiteSemValidade);
        detalhes.put("diasBloqueioValidade", diasBloqueio);
        detalhes.put("operacaoId", operacao);
        auditoria.registrar("ESTOQUE_REGULARIZADO", "produto", produtoId, detalhes);
        LocalDate hoje = loteService.hoje();
        int margem = loteService.margemDeBloqueio(p);
        return lotes.findByProdutoIdOrderByIdAsc(produtoId).stream().map(l -> LoteResponse.of(l, hoje, margem)).toList();
    }

    /**
     * Desliga o controle. Só vale para produto que nunca teve lote: com qualquer lote (mesmo zerado) o histórico de rastreio
     * ficaria órfão, e esse procedimento não existe neste ciclo (D12, endurecido).
     */
    @Transactional
    public void desativarControle(Long produtoId) {
        Produto p = produtos.travar(produtoId).orElseThrow(() -> new NotFoundException("Produto", produtoId));
        if (!p.isControlaLote()) {
            throw new ConflictException("O produto não controla lote.");
        }
        if (!lotes.travarDoProduto(produtoId).isEmpty()) {
            throw new ConflictException("O produto tem lotes e histórico de rastreio; não é possível desligar o controle de lote.");
        }
        p.desativarControleLote();
        produtos.save(p);
        auditoria.registrar("CONTROLE_LOTE_DESATIVADO", "produto", produtoId, Map.of("produtoId", produtoId));
    }

    @Transactional
    public LoteResponse bloquear(Long produtoId, Long loteId, String motivo) {
        return mudarStatus(produtoId, loteId, motivo, StatusLote.BLOQUEADO, "LOTE_BLOQUEADO");
    }

    @Transactional
    public LoteResponse desbloquear(Long produtoId, Long loteId, String motivo) {
        return mudarStatus(produtoId, loteId, motivo, StatusLote.DISPONIVEL, "LOTE_DESBLOQUEADO");
    }

    private LoteResponse mudarStatus(Long produtoId, Long loteId, String motivo, StatusLote novo, String evento) {
        exigirHabilitado();
        String texto = motivoValido(motivo);
        Produto p = produtoControlado(produtoId);
        LoteEstoque lote = loteDoProduto(p, loteId);
        if (lote.getStatus() == novo) {
            throw new ConflictException("O lote já está " + (novo == StatusLote.BLOQUEADO ? "bloqueado." : "disponível."));
        }
        if (novo == StatusLote.BLOQUEADO) {
            lote.bloquear();
        } else {
            lote.desbloquear();
        }
        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("produtoId", produtoId);
        detalhes.put("loteId", loteId);
        detalhes.put("codigoLote", lote.getCodigoLote());
        detalhes.put("motivo", texto);
        auditoria.registrar(evento, "lote", loteId, detalhes);
        return LoteResponse.of(lote, loteService.hoje(), loteService.margemDeBloqueio(p));
    }

    // -------------------------------------------------------------------- internos

    /** Núcleo: lote + total do produto + movimento, tudo com o produto travado pelo chamador. */
    private MovimentacaoEstoque mover(Produto p, LoteEstoque lote, TipoMovimentacao tipo, int delta, Compra compra, Long vendaId,
            Usuario usuario, String motivo, String operacaoId, boolean alteraTotal) {
        int anteriorLote = lote.movimentar(delta);
        int anteriorTotal = alteraTotal ? p.movimentar(delta) : p.getQuantidadeEstoque();
        produtos.save(p);
        return movimentacoes.save(new MovimentacaoEstoque(p, tipo, delta, anteriorTotal, p.getQuantidadeEstoque(), compra, vendaId,
                usuario, motivo, lote, anteriorLote, operacaoId));
    }

    private LoteEstoque obterOuCriarAuditado(Produto p, LoteInformado info, BigDecimal custo, LocalDate dataRecebimento,
            Compra compra, Usuario usuario) {
        String codigo;
        try {
            codigo = LoteEstoque.normalizarCodigo(info.codigoLote());
        } catch (IllegalArgumentException e) {
            throw new CampoInvalidoException("codigoLote", e.getMessage());
        }
        boolean existia = lotes.travarPorProdutoECodigo(p.getId(), codigo).isPresent();
        LoteEstoque lote = loteService.obterOuCriar(p, codigo, info.validade(), custo, dataRecebimento, compra,
                compra == null ? null : compra.getFornecedor(), usuario);
        if (!existia) {
            Map<String, Object> detalhes = new LinkedHashMap<>();
            detalhes.put("produtoId", p.getId());
            detalhes.put("codigoLote", codigo);
            detalhes.put("validade", info.validade() == null ? null : info.validade().toString());
            auditoria.registrar("LOTE_CRIADO", "lote", lote.getId(), detalhes);
        }
        return lote;
    }

    /** Código válido e único na lista, validade conforme a política do produto, soma igual ao esperado. */
    private void validarLotes(Produto p, List<LoteInformado> infos, int esperado, boolean recebimento) {
        if (infos.isEmpty() && esperado > 0) {
            throw new CampoInvalidoException("lotes", "Informe os lotes do produto " + p.getNome() + ".");
        }
        Set<String> codigos = new HashSet<>();
        long soma = 0;
        LocalDate hoje = loteService.hoje();
        for (LoteInformado info : infos) {
            String codigo = info.codigoLote() == null ? "" : info.codigoLote().trim().toLowerCase(Locale.ROOT);
            if (codigo.isEmpty() || !codigos.add(codigo)) {
                throw new CampoInvalidoException("lotes", "Código de lote vazio ou repetido para o produto " + p.getNome() + ".");
            }
            if (info.validade() == null && !p.isPermiteSemValidade()) {
                throw new CampoInvalidoException("lotes", "O produto " + p.getNome() + " exige validade em todos os lotes.");
            }
            if (recebimento && info.validade() != null && info.validade().isBefore(hoje)) {
                throw new CampoInvalidoException("lotes", "O lote " + info.codigoLote() + " já está vencido e não pode ser recebido.");
            }
            soma += info.quantidade();
        }
        if (soma != esperado) {
            throw new CampoInvalidoException("lotes", "Os lotes de " + p.getNome() + " somam " + soma
                    + ", mas a quantidade esperada é " + esperado + ".");
        }
    }

    private Produto produtoControlado(Long produtoId) {
        Produto p = produtos.travar(produtoId).orElseThrow(() -> new NotFoundException("Produto", produtoId));
        if (!p.isControlaLote()) {
            throw new CampoInvalidoException("produtoId", "O produto não controla lote.");
        }
        return p;
    }

    private LoteEstoque loteDoProduto(Produto p, Long loteId) {
        return lotes.travar(loteId).filter(l -> l.getProduto().getId().equals(p.getId()))
                .orElseThrow(() -> new NotFoundException("Lote", loteId));
    }

    private void exigirHabilitado() {
        if (!loteService.habilitado()) {
            throw new ConflictException(LOTES_DESLIGADOS);
        }
    }

    private static String motivoValido(String motivo) {
        String texto = motivo == null ? "" : motivo.trim();
        if (texto.length() < 5 || texto.length() > 500) {
            throw new CampoInvalidoException("motivo", "O motivo deve ter entre 5 e 500 caracteres.");
        }
        return texto;
    }

    private static Map<String, Object> detalhesMovimento(MovimentacaoEstoque m, String motivo) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("produtoId", m.getProduto().getId());
        d.put("loteId", m.getLote().getId());
        d.put("codigoLote", m.getLote().getCodigoLote());
        d.put("quantidade", m.getQuantidade());
        d.put("saldoAnterior", m.getSaldoAnterior());
        d.put("saldoResultante", m.getSaldoResultante());
        d.put("saldoLoteAnterior", m.getLoteSaldoAnterior());
        d.put("saldoLoteResultante", m.getLoteSaldoResultante());
        d.put("motivo", motivo);
        return d;
    }

    private Usuario usuario(Long usuarioId) {
        return usuarios.findById(usuarioId).orElseThrow(() -> new NotFoundException("Usuário", usuarioId));
    }
}
