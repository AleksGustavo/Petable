package br.com.tecnodreamer.petconnect.financeiro.application;

import br.com.tecnodreamer.petconnect.agendamento.domain.ServicoRealizado;
import br.com.tecnodreamer.petconnect.compra.domain.Compra;
import br.com.tecnodreamer.petconnect.financeiro.domain.Caixa;
import br.com.tecnodreamer.petconnect.financeiro.domain.CaixaMovimento;
import br.com.tecnodreamer.petconnect.financeiro.domain.CategoriaLancamento;
import br.com.tecnodreamer.petconnect.financeiro.domain.ContaPagar;
import br.com.tecnodreamer.petconnect.financeiro.domain.Lancamento;
import br.com.tecnodreamer.petconnect.financeiro.domain.StatusCaixa;
import br.com.tecnodreamer.petconnect.financeiro.domain.StatusConta;
import br.com.tecnodreamer.petconnect.financeiro.domain.TipoLancamento;
import br.com.tecnodreamer.petconnect.financeiro.domain.TipoMovimentoCaixa;
import br.com.tecnodreamer.petconnect.financeiro.infrastructure.CaixaRepository;
import br.com.tecnodreamer.petconnect.financeiro.infrastructure.ContaPagarRepository;
import br.com.tecnodreamer.petconnect.financeiro.infrastructure.LancamentoRepository;
import br.com.tecnodreamer.petconnect.financeiro.web.ContaPagarResponse;
import br.com.tecnodreamer.petconnect.financeiro.web.FluxoResponse;
import br.com.tecnodreamer.petconnect.financeiro.web.LancamentoManualRequest;
import br.com.tecnodreamer.petconnect.financeiro.web.LancamentoResponse;
import br.com.tecnodreamer.petconnect.financeiro.web.ResumoFinanceiroDiaResponse;
import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;
import br.com.tecnodreamer.petconnect.shared.error.ConflictException;
import br.com.tecnodreamer.petconnect.shared.error.NotFoundException;
import br.com.tecnodreamer.petconnect.usuario.domain.Usuario;
import br.com.tecnodreamer.petconnect.usuario.infrastructure.UsuarioRepository;
import br.com.tecnodreamer.petconnect.venda.domain.FormaPagamento;
import br.com.tecnodreamer.petconnect.venda.domain.Venda;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Serviço financeiro central (DA-01): o ÚNICO componente que grava
 * {@code movimentacao_financeira}. Chamado dentro da transação do módulo de origem
 * (venda, cancelamento, conclusão de serviço, pagamento), então qualquer falha
 * desfaz tudo (DA-05). Cada lançamento tem {@code chave_origem} UNIQUE (DA-06).
 */
@Service
public class FinanceiroService {

    public static final String CAIXA_AUSENTE = "Não há caixa aberto para o operador atual. Abra o caixa antes de continuar.";
    public static final String JA_ESTORNADO = "Este lançamento já foi estornado.";
    static final String ENTIDADE = "lancamento";

    private final LancamentoRepository lancamentos;
    private final ContaPagarRepository contas;
    private final CaixaRepository caixas;
    private final UsuarioRepository usuarios;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public FinanceiroService(LancamentoRepository lancamentos, ContaPagarRepository contas, CaixaRepository caixas,
            UsuarioRepository usuarios, AuditoriaService auditoria, Clock clock) {
        this.lancamentos = lancamentos;
        this.contas = contas;
        this.caixas = caixas;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ automáticos (chamados pelos módulos de origem)

    /** Venda concluída → ENTRADA / VENDA_PRODUTO no caixa do operador. */
    @Transactional
    public Lancamento registrarVenda(Venda venda, Caixa caixa, Long usuarioId) {
        return gravar(TipoLancamento.ENTRADA, CategoriaLancamento.VENDA_PRODUTO, "Venda #" + venda.getId(), venda.getValorTotal(),
                venda.getFormaPagamento(), hoje(), usuario(usuarioId), caixa, Lancamento.Origem.venda(venda.getId()),
                "VENDA:" + venda.getId(), null, null, null);
    }

    /**
     * Cancelamento → SAIDA / VENDA_PRODUTO espelhando a entrada original (DA-07), no
     * caixa aberto de quem cancela. Venda anterior à V8 sem lançamento (backfill não
     * executado) não tem o que estornar: devolve vazio.
     */
    @Transactional
    public Optional<Lancamento> registrarEstornoVenda(Venda venda, Caixa caixa, Long usuarioId, String motivo) {
        return lancamentos.findByChaveOrigem("VENDA:" + venda.getId()).map(original -> {
            if (lancamentos.existsByEstornoDeId(original.getId())) {
                throw new ConflictException(JA_ESTORNADO);
            }
            return gravar(TipoLancamento.SAIDA, CategoriaLancamento.VENDA_PRODUTO, "Estorno da venda #" + venda.getId(),
                    original.getValor(), original.getFormaPagamento(), hoje(), usuario(usuarioId), caixa,
                    Lancamento.Origem.venda(venda.getId()), "VENDA_ESTORNO:" + venda.getId(), original.getId(), motivo, null);
        });
    }

    /** Serviço concluído → ENTRADA / SERVICO (DA-08). Serviço de valor zero não gera receita (o CHECK exige valor > 0). */
    @Transactional
    public Optional<Lancamento> registrarServico(ServicoRealizado servico, FormaPagamento forma, Caixa caixa, Long usuarioId) {
        if (servico.getValor() == null || servico.getValor().signum() <= 0) {
            return Optional.empty();
        }
        return Optional.of(gravar(TipoLancamento.ENTRADA, CategoriaLancamento.SERVICO,
                limitar("Serviço " + servico.getServico().getNome() + " #" + servico.getId()), servico.getValor(), forma, hoje(),
                usuario(usuarioId), caixa, Lancamento.Origem.servico(servico.getId()), "SERVICO:" + servico.getId(), null,
                null, null));
    }

    /** Recebimento de compra → conta a pagar ABERTA, sem saída (DA-09). Uma por compra (UNIQUE). */
    @Transactional
    public ContaPagar criarContaPagar(Compra compra, LocalDate vencimento) {
        if (contas.findByCompraId(compra.getId()).isPresent()) {
            throw new ConflictException("Esta compra já possui conta a pagar.");
        }
        ContaPagar conta = contas.saveAndFlush(new ContaPagar(compra, "Compra #" + compra.getId(), compra.getValorTotal(),
                vencimento));
        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("compraId", compra.getId());
        detalhes.put("valor", conta.getValorOriginal());
        detalhes.put("dataVencimento", vencimento.toString());
        auditoria.registrar("CONTA_PAGAR_CRIADA", "conta_pagar", conta.getId(), detalhes);
        return conta;
    }

    /** Sangria (SAIDA) ou suprimento (ENTRADA) em dinheiro, categoria OUTROS (DA-15). */
    @Transactional
    public Lancamento registrarMovimentoCaixa(CaixaMovimento m, Long usuarioId) {
        boolean sangria = m.getTipo() == TipoMovimentoCaixa.SANGRIA;
        return gravar(sangria ? TipoLancamento.SAIDA : TipoLancamento.ENTRADA, CategoriaLancamento.OUTROS,
                (sangria ? "Sangria" : "Suprimento") + " do caixa #" + m.getCaixa().getId(), m.getValor(),
                FormaPagamento.DINHEIRO, hoje(), usuario(usuarioId), m.getCaixa(), Lancamento.Origem.NENHUMA,
                (sangria ? "CAIXA_SANGRIA:" : "CAIXA_SUPRIMENTO:") + m.getId(), null, null, limitar500(m.getMotivo()));
    }

    // ------------------------------------------------------------------ contas a pagar

    @Transactional(readOnly = true)
    public Page<ContaPagarResponse> contas(StatusConta status, String situacao, LocalDate de, LocalDate ate, Pageable pageable) {
        LocalDate hoje = hoje();
        StatusConta filtroStatus = status;
        LocalDate venceDe = de;
        LocalDate venceAte = ate;
        switch (situacao == null ? "TODAS" : situacao) {
            case "VENCIDA" -> {
                filtroStatus = StatusConta.ABERTA;
                venceAte = menor(venceAte, hoje.minusDays(1));
            }
            case "VENCE_HOJE" -> {
                filtroStatus = StatusConta.ABERTA;
                venceDe = maior(venceDe, hoje);
                venceAte = menor(venceAte, hoje);
            }
            case "A_VENCER" -> {
                filtroStatus = StatusConta.ABERTA;
                venceDe = maior(venceDe, hoje.plusDays(1));
            }
            case "TODAS" -> {
            }
            default -> throw new CampoInvalidoException("situacao", "Situação inválida.");
        }
        return contas.buscar(filtroStatus, venceDe, venceAte, pageable).map(c -> ContaPagarResponse.of(c, hoje, null));
    }

    @Transactional(readOnly = true)
    public ContaPagarResponse conta(Long id) {
        ContaPagar c = contas.findWithDetalhesById(id).orElseThrow(() -> new NotFoundException("Conta a pagar", id));
        return ContaPagarResponse.of(c, hoje(), lancamentoDaConta(id));
    }

    @Transactional(readOnly = true)
    public Optional<ContaPagar> contaDaCompra(Long compraId) {
        return contas.findByCompraId(compraId);
    }

    /** Pagamento integral sob lock (DA-11): SAIDA / COMPRA_MERCADORIA no caixa aberto de quem paga. */
    @Transactional
    public ContaPagarResponse pagar(Long id, FormaPagamento forma, Long usuarioId) {
        ContaPagar conta = contas.travar(id).orElseThrow(() -> new NotFoundException("Conta a pagar", id));
        if (conta.getStatus() == StatusConta.PAGA) {
            throw new ConflictException(ContaPagar.JA_PAGA);
        }
        Caixa caixa = caixaAbertoTravado(usuarioId);
        Usuario usuario = usuario(usuarioId);
        conta.pagar(forma, usuario, LocalDateTime.now(clock));
        contas.saveAndFlush(conta);
        Long compraId = conta.getCompra() == null ? null : conta.getCompra().getId();
        Lancamento l = gravar(TipoLancamento.SAIDA, CategoriaLancamento.COMPRA_MERCADORIA, conta.getDescricao(),
                conta.getValorOriginal(), forma, hoje(), usuario, caixa, Lancamento.Origem.conta(compraId, conta.getId()),
                "CONTA_PAGAR:" + conta.getId(), null, null, null);
        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("valor", conta.getValorOriginal());
        detalhes.put("formaPagamento", forma.name());
        detalhes.put("lancamentoId", l.getId());
        auditoria.registrar("CONTA_PAGA", "conta_pagar", id, detalhes);
        return ContaPagarResponse.of(contas.findWithDetalhesById(id).orElse(conta), hoje(), l.getId());
    }

    // ------------------------------------------------------------------ manuais (DA-12, DA-13)

    /**
     * Manual com Idempotency-Key: a mesma chave do mesmo usuário devolve o lançamento
     * original sem duplicar; de outro usuário, 409. A corrida é fechada pela UNIQUE de
     * chave_origem em {@link LancamentoManualIdempotente}.
     */
    @Transactional
    public ResultadoLancamento lancarManual(LancamentoManualRequest req, String chave, Long usuarioId) {
        String chaveOrigem = "MANUAL:" + chaveValida(chave);
        Optional<Lancamento> existente = lancamentos.findByChaveOrigem(chaveOrigem);
        if (existente.isPresent()) {
            return new ResultadoLancamento(LancamentoResponse.of(doMesmoUsuario(existente.get(), usuarioId)), true);
        }
        if (!req.categoria().manual()) {
            throw new CampoInvalidoException("categoria",
                    "Lançamento manual aceita só Despesa com funcionário, Despesa operacional ou Outros.");
        }
        Caixa caixa = caixaAbertoTravado(usuarioId);
        Lancamento l = gravar(req.tipo(), req.categoria(), req.descricao().trim(), req.valor(), req.formaPagamento(), hoje(),
                usuario(usuarioId), caixa, Lancamento.Origem.NENHUMA, chaveOrigem, null, null, limpo(req.observacoes()));
        return new ResultadoLancamento(LancamentoResponse.of(l), false);
    }

    /** Só lançamento manual é estornado diretamente; automáticos são corrigidos pelo módulo de origem (DA-13). */
    @Transactional
    public LancamentoResponse estornar(Long id, String motivo, Long usuarioId) {
        String texto = motivo == null ? "" : motivo.trim();
        if (texto.length() < 5 || texto.length() > 500) {
            throw new CampoInvalidoException("motivo", "O motivo deve ter entre 5 e 500 caracteres.");
        }
        Lancamento original = lancamentos.findById(id).orElseThrow(() -> new NotFoundException("Lançamento", id));
        if (!original.isManual() || original.getEstornoDeId() != null) {
            throw new ConflictException("Somente lançamentos manuais podem ser estornados aqui. "
                    + "Lançamentos automáticos são corrigidos pelo módulo de origem.");
        }
        if (lancamentos.existsByEstornoDeId(id)) {
            throw new ConflictException(JA_ESTORNADO);
        }
        Caixa caixa = caixaAbertoTravado(usuarioId);
        Lancamento estorno = gravar(original.getTipo().inverso(), original.getCategoria(),
                limitar("Estorno do lançamento #" + id), original.getValor(), original.getFormaPagamento(), hoje(),
                usuario(usuarioId), caixa, Lancamento.Origem.NENHUMA, "MANUAL_ESTORNO:" + id, id, texto, null);
        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("estornoId", estorno.getId());
        detalhes.put("motivo", texto);
        auditoria.registrar("LANCAMENTO_ESTORNADO", ENTIDADE, id, detalhes);
        return LancamentoResponse.of(estorno);
    }

    // ------------------------------------------------------------------ consultas

    @Transactional(readOnly = true)
    public Page<LancamentoResponse> listar(LocalDate inicio, LocalDate fim, TipoLancamento tipo, CategoriaLancamento categoria,
            FormaPagamento forma, String origem, Pageable pageable) {
        validarPeriodo(inicio, fim);
        String prefixo = origem == null ? null : switch (origem) {
            case "VENDA", "SERVICO", "CONTA_PAGAR", "MANUAL", "CAIXA" -> origem + "%";
            default -> throw new CampoInvalidoException("origem", "Origem inválida.");
        };
        return lancamentos.buscar(inicio, fim, tipo, categoria, forma, prefixo, pageable).map(LancamentoResponse::of);
    }

    @Transactional(readOnly = true)
    public LancamentoResponse detalhe(Long id) {
        Lancamento l = lancamentos.findById(id).orElseThrow(() -> new NotFoundException("Lançamento", id));
        return LancamentoResponse.of(l, lancamentos.findByEstornoDeId(id).map(Lancamento::getId).orElse(null));
    }

    @Transactional(readOnly = true)
    public Optional<Lancamento> porChave(String chaveOrigem) {
        return lancamentos.findByChaveOrigem(chaveOrigem);
    }

    /** Fluxo por período, agregado no banco (§11). */
    @Transactional(readOnly = true)
    public FluxoResponse fluxo(LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null) {
            throw new CampoInvalidoException(inicio == null ? "dataInicio" : "dataFim", "Informe o período.");
        }
        validarPeriodo(inicio, fim);
        List<FluxoResponse.PorCategoria> categorias = new ArrayList<>();
        BigDecimal entradas = zero();
        BigDecimal saidas = zero();
        for (Object[] r : lancamentos.fluxoPorCategoria(inicio, fim)) {
            BigDecimal e = decimal(r[1]);
            BigDecimal s = decimal(r[2]);
            entradas = entradas.add(e);
            saidas = saidas.add(s);
            categorias.add(new FluxoResponse.PorCategoria(String.valueOf(r[0]), e, s, e.subtract(s)));
        }
        List<FluxoResponse.PorForma> formas = new ArrayList<>();
        for (Object[] r : lancamentos.fluxoPorForma(inicio, fim)) {
            BigDecimal e = decimal(r[1]);
            BigDecimal s = decimal(r[2]);
            formas.add(new FluxoResponse.PorForma(r[0] == null ? "NAO_INFORMADA" : String.valueOf(r[0]), e, s, e.subtract(s)));
        }
        return new FluxoResponse(inicio, fim, entradas, saidas, entradas.subtract(saidas), categorias, formas);
    }

    @Transactional(readOnly = true)
    public ResumoFinanceiroDiaResponse resumoDia(LocalDate data) {
        LocalDate dia = data == null ? hoje() : data;
        FluxoResponse f = fluxo(dia, dia);
        ContaPagarRepository.Resumo vencidas = contas.resumirAbertas(LocalDate.of(1900, 1, 1), dia.minusDays(1));
        ContaPagarRepository.Resumo aVencer = contas.resumirAbertas(dia, dia.plusDays(7));
        return new ResumoFinanceiroDiaResponse(dia, f.entradas(), f.saidas(), f.saldo(), quantidade(vencidas),
                total(vencidas), quantidade(aVencer), total(aVencer), caixas.countByStatus(StatusCaixa.ABERTO));
    }

    // ------------------------------------------------------------------ histórico (backfill, DA-18)

    @Transactional
    public Lancamento registrarVendaHistorica(Venda venda) {
        return gravar(TipoLancamento.ENTRADA, CategoriaLancamento.VENDA_PRODUTO, "Venda #" + venda.getId(), venda.getValorTotal(),
                venda.getFormaPagamento(), venda.getDataVenda().toLocalDate(), venda.getUsuario(), null,
                Lancamento.Origem.venda(venda.getId()), "VENDA:" + venda.getId(), null, null, "Histórico (backfill)");
    }

    @Transactional
    public Optional<Lancamento> registrarEstornoHistorico(Venda venda) {
        return lancamentos.findByChaveOrigem("VENDA:" + venda.getId()).map(original -> gravar(TipoLancamento.SAIDA,
                CategoriaLancamento.VENDA_PRODUTO, "Estorno da venda #" + venda.getId(), original.getValor(),
                original.getFormaPagamento(), venda.getCanceladaEm().toLocalDate(), venda.getCanceladaPor(), null,
                Lancamento.Origem.venda(venda.getId()), "VENDA_ESTORNO:" + venda.getId(), original.getId(),
                limitar500(venda.getMotivoCancelamento()), "Histórico (backfill)"));
    }

    @Transactional
    public Lancamento registrarServicoHistorico(ServicoRealizado s, FormaPagamento forma) {
        return gravar(TipoLancamento.ENTRADA, CategoriaLancamento.SERVICO,
                limitar("Serviço " + s.getServico().getNome() + " #" + s.getId()), s.getValor(), forma,
                s.getDataServico().toLocalDate(), null, null, Lancamento.Origem.servico(s.getId()), "SERVICO:" + s.getId(),
                null, null, "Histórico (backfill)");
    }

    /**
     * Migração (etapa 08): lançamento do sistema antigo que não é venda nem serviço (esses o
     * backfill regenera). Idempotente pela chave {@code LEGADO:<id>} (UNIQUE de chave_origem).
     */
    @Transactional
    public Lancamento registrarLegado(TipoLancamento tipo, CategoriaLancamento categoria, String descricao, BigDecimal valor,
            LocalDate data, Long idLegado, String observacoes) {
        String chave = "LEGADO:" + idLegado;
        return lancamentos.findByChaveOrigem(chave).orElseGet(() -> gravar(tipo, categoria, limitar(descricao), valor, null,
                data, null, null, Lancamento.Origem.NENHUMA, chave, null, null, observacoes));
    }

    // ------------------------------------------------------------------ núcleo

    private Lancamento gravar(TipoLancamento tipo, CategoriaLancamento categoria, String descricao, BigDecimal valor,
            FormaPagamento forma, LocalDate data, Usuario usuario, Caixa caixa, Lancamento.Origem origem, String chave,
            Long estornoDeId, String motivoEstorno, String observacoes) {
        BigDecimal v = valor.setScale(2, RoundingMode.HALF_UP);
        if (v.signum() <= 0) {
            throw new IllegalArgumentException("Lançamento exige valor positivo.");
        }
        Lancamento l = lancamentos.saveAndFlush(new Lancamento(tipo, categoria, descricao, v, forma, data, usuario, caixa,
                origem, chave, estornoDeId, motivoEstorno, observacoes));
        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("tipo", tipo.name());
        detalhes.put("categoria", categoria.name());
        detalhes.put("valor", v);
        detalhes.put("formaPagamento", forma == null ? null : forma.name());
        detalhes.put("origem", chave);
        detalhes.put("caixaId", caixa == null ? null : caixa.getId());
        auditoria.registrar("LANCAMENTO_CRIADO", ENTIDADE, l.getId(), detalhes);
        return l;
    }

    private Caixa caixaAbertoTravado(Long usuarioId) {
        return caixas.travarAbertoDoUsuario(usuarioId).orElseThrow(() -> new ConflictException(CAIXA_AUSENTE));
    }

    private Long lancamentoDaConta(Long contaId) {
        return lancamentos.findByChaveOrigem("CONTA_PAGAR:" + contaId).map(Lancamento::getId).orElse(null);
    }

    static String chaveValida(String chave) {
        if (chave == null || chave.isBlank()) {
            throw new CampoInvalidoException("idempotencyKey", "Envie o cabeçalho Idempotency-Key.");
        }
        try {
            return UUID.fromString(chave.trim()).toString();
        } catch (IllegalArgumentException e) {
            throw new CampoInvalidoException("idempotencyKey", "Idempotency-Key deve ser um UUID.");
        }
    }

    static Lancamento doMesmoUsuario(Lancamento l, Long usuarioId) {
        if (l.getUsuario() == null || !l.getUsuario().getId().equals(usuarioId)) {
            throw new ConflictException("Chave de idempotência já utilizada.");
        }
        return l;
    }

    private Usuario usuario(Long usuarioId) {
        return usuarios.findById(usuarioId).orElseThrow(() -> new NotFoundException("Usuário", usuarioId));
    }

    private LocalDate hoje() {
        return LocalDate.now(clock);
    }

    private static void validarPeriodo(LocalDate inicio, LocalDate fim) {
        if (inicio != null && fim != null && fim.isBefore(inicio)) {
            throw new CampoInvalidoException("dataFim", "A data final não pode ser anterior à inicial.");
        }
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2);
    }

    static BigDecimal decimal(Object o) {
        if (o == null) {
            return zero();
        }
        BigDecimal b = o instanceof BigDecimal bd ? bd : new BigDecimal(o.toString());
        return b.setScale(2, RoundingMode.HALF_UP);
    }

    private static long quantidade(ContaPagarRepository.Resumo r) {
        return r == null || r.getQuantidade() == null ? 0 : r.getQuantidade();
    }

    private static BigDecimal total(ContaPagarRepository.Resumo r) {
        return r == null || r.getTotal() == null ? zero() : r.getTotal().setScale(2, RoundingMode.HALF_UP);
    }

    private static LocalDate maior(LocalDate a, LocalDate b) {
        return a == null || b.isAfter(a) ? b : a;
    }

    private static LocalDate menor(LocalDate a, LocalDate b) {
        return a == null || b.isBefore(a) ? b : a;
    }

    private static String limitar(String s) {
        return s.length() <= 100 ? s : s.substring(0, 100);
    }

    private static String limitar500(String s) {
        return s == null ? null : s.length() <= 500 ? s : s.substring(0, 500);
    }

    private static String limpo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
