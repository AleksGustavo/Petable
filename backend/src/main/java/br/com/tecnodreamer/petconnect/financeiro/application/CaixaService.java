package br.com.tecnodreamer.petconnect.financeiro.application;

import br.com.tecnodreamer.petconnect.financeiro.domain.Caixa;
import br.com.tecnodreamer.petconnect.financeiro.domain.CaixaMovimento;
import br.com.tecnodreamer.petconnect.financeiro.domain.StatusCaixa;
import br.com.tecnodreamer.petconnect.financeiro.domain.TipoMovimentoCaixa;
import br.com.tecnodreamer.petconnect.financeiro.infrastructure.CaixaMovimentoRepository;
import br.com.tecnodreamer.petconnect.financeiro.infrastructure.CaixaRepository;
import br.com.tecnodreamer.petconnect.financeiro.infrastructure.LancamentoRepository;
import br.com.tecnodreamer.petconnect.financeiro.web.CaixaResponse;
import br.com.tecnodreamer.petconnect.financeiro.web.PreviaFechamentoResponse;
import br.com.tecnodreamer.petconnect.financeiro.web.ResumoCaixaAtualResponse;
import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;
import br.com.tecnodreamer.petconnect.shared.error.ConflictException;
import br.com.tecnodreamer.petconnect.shared.error.NotFoundException;
import br.com.tecnodreamer.petconnect.usuario.domain.Usuario;
import br.com.tecnodreamer.petconnect.usuario.infrastructure.UsuarioRepository;
import br.com.tecnodreamer.petconnect.venda.domain.FormaPagamento;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Caixa do operador (DA-03, DA-04, DA-14–DA-16). Dinheiro esperado =
 * fundo + Σ lançamentos em DINHEIRO do caixa (entradas − saídas). Sangria e
 * suprimento já são lançamentos em DINHEIRO, então entram uma única vez.
 */
@Service
public class CaixaService {

    public static final String JA_ABERTO = "Já existe um caixa aberto para o usuário atual.";
    public static final String SANGRIA_EXCEDE = "A sangria não pode exceder o saldo esperado em dinheiro.";
    static final String ENTIDADE = "caixa";

    private final CaixaRepository caixas;
    private final CaixaMovimentoRepository movimentos;
    private final LancamentoRepository lancamentos;
    private final FinanceiroService financeiro;
    private final UsuarioRepository usuarios;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public CaixaService(CaixaRepository caixas, CaixaMovimentoRepository movimentos, LancamentoRepository lancamentos,
            FinanceiroService financeiro, UsuarioRepository usuarios, AuditoriaService auditoria, Clock clock) {
        this.caixas = caixas;
        this.movimentos = movimentos;
        this.lancamentos = lancamentos;
        this.financeiro = financeiro;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ usado por venda/serviço (DA-03, DA-08)

    /** Falha rápida, sem lock (antes de travar produtos): 409 se o usuário não tem caixa aberto. */
    @Transactional(readOnly = true)
    public void exigirAberto(Long usuarioId) {
        if (usuarioId == null || caixas.findByUsuarioIdAndStatus(usuarioId, StatusCaixa.ABERTO).isEmpty()) {
            throw new ConflictException(FinanceiroService.CAIXA_AUSENTE);
        }
    }

    /** Trava o caixa aberto do usuário (depois dos produtos: mesma ordem em todos os fluxos, sem deadlock). */
    @Transactional
    public Caixa travarAberto(Long usuarioId) {
        if (usuarioId == null) {
            throw new ConflictException(FinanceiroService.CAIXA_AUSENTE);
        }
        return caixas.travarAbertoDoUsuario(usuarioId).orElseThrow(() -> new ConflictException(FinanceiroService.CAIXA_AUSENTE));
    }

    // ------------------------------------------------------------------ operação

    @Transactional
    public CaixaResponse abrir(Long usuarioId, BigDecimal fundo) {
        if (caixas.findByUsuarioIdAndStatus(usuarioId, StatusCaixa.ABERTO).isPresent()) {
            throw new ConflictException(JA_ABERTO);
        }
        Caixa caixa;
        try {
            caixa = caixas.saveAndFlush(new Caixa(usuario(usuarioId), dinheiro(fundo), LocalDateTime.now(clock)));
        } catch (DataIntegrityViolationException e) {
            // Duas aberturas simultâneas: a UNIQUE da coluna gerada deixa só uma passar (DA-04).
            throw new ConflictException(JA_ABERTO);
        }
        auditoria.registrar("CAIXA_ABERTO", ENTIDADE, caixa.getId(), Map.of("fundoAbertura", caixa.getFundoAbertura()));
        return CaixaResponse.of(caixa, esperado(caixa), java.util.List.of());
    }

    @Transactional(readOnly = true)
    public CaixaResponse atual(Long usuarioId) {
        Caixa caixa = caixas.findByUsuarioIdAndStatus(usuarioId, StatusCaixa.ABERTO)
                .orElseThrow(() -> new NotFoundException("Caixa aberto do usuário", usuarioId));
        return resposta(caixa);
    }

    @Transactional(readOnly = true)
    public ResumoCaixaAtualResponse resumoAtual(Long usuarioId) {
        return caixas.findByUsuarioIdAndStatus(usuarioId, StatusCaixa.ABERTO)
                .map(c -> new ResumoCaixaAtualResponse(true, c.getId(), c.getAbertoEm(), esperado(c).get(FormaPagamento.DINHEIRO)))
                .orElse(new ResumoCaixaAtualResponse(false, null, null, null));
    }

    @Transactional
    public CaixaResponse.Movimento suprimento(Long usuarioId, BigDecimal valor, String motivo) {
        return movimentar(usuarioId, TipoMovimentoCaixa.SUPRIMENTO, valor, motivo);
    }

    @Transactional
    public CaixaResponse.Movimento sangria(Long usuarioId, BigDecimal valor, String motivo) {
        return movimentar(usuarioId, TipoMovimentoCaixa.SANGRIA, valor, motivo);
    }

    private CaixaResponse.Movimento movimentar(Long usuarioId, TipoMovimentoCaixa tipo, BigDecimal valor, String motivo) {
        String texto = motivo == null ? "" : motivo.trim();
        if (texto.length() < 5 || texto.length() > 500) {
            throw new CampoInvalidoException("motivo", "O motivo deve ter entre 5 e 500 caracteres.");
        }
        BigDecimal v = dinheiro(valor);
        if (v.signum() <= 0) {
            throw new CampoInvalidoException("valor", "O valor deve ser maior que zero.");
        }
        Caixa caixa = travarAberto(usuarioId);
        if (tipo == TipoMovimentoCaixa.SANGRIA && v.compareTo(esperado(caixa).get(FormaPagamento.DINHEIRO)) > 0) {
            throw new ConflictException(SANGRIA_EXCEDE);
        }
        Usuario usuario = usuario(usuarioId);
        CaixaMovimento m = movimentos.saveAndFlush(new CaixaMovimento(caixa, tipo, v, texto, usuario));
        financeiro.registrarMovimentoCaixa(m, usuarioId);
        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("valor", v);
        detalhes.put("motivo", texto);
        auditoria.registrar(tipo == TipoMovimentoCaixa.SANGRIA ? "CAIXA_SANGRIA" : "CAIXA_SUPRIMENTO", ENTIDADE, caixa.getId(),
                detalhes);
        return CaixaResponse.Movimento.of(m);
    }

    @Transactional(readOnly = true)
    public PreviaFechamentoResponse previaFechamento(Long usuarioId) {
        Caixa caixa = caixas.findByUsuarioIdAndStatus(usuarioId, StatusCaixa.ABERTO)
                .orElseThrow(() -> new ConflictException(FinanceiroService.CAIXA_AUSENTE));
        Map<FormaPagamento, BigDecimal> e = esperado(caixa);
        return new PreviaFechamentoResponse(caixa.getId(), e, soma(e));
    }

    /** Fechamento definitivo sob lock (DA-16): diferença = contado − esperado; qualquer diferença exige motivo. */
    @Transactional
    public CaixaResponse fechar(Long usuarioId, Map<FormaPagamento, BigDecimal> contadoInformado, String motivo) {
        Map<FormaPagamento, BigDecimal> contado = new EnumMap<>(FormaPagamento.class);
        for (FormaPagamento f : FormaPagamento.values()) {
            BigDecimal v = contadoInformado == null ? null : contadoInformado.get(f);
            if (v == null) {
                throw new CampoInvalidoException("contado." + f.name(), "Informe o valor contado.");
            }
            // Dinheiro físico não fica negativo; cartão/Pix/transferência são o LÍQUIDO conferido no
            // extrato (entradas − saídas do caixa) e podem ser negativos (ex.: conta paga por Pix).
            if ((f == FormaPagamento.DINHEIRO && v.signum() < 0) || v.stripTrailingZeros().scale() > 2) {
                throw new CampoInvalidoException("contado." + f.name(), "Valor inválido.");
            }
            contado.put(f, dinheiro(v));
        }
        Caixa caixa = travarAberto(usuarioId);
        Map<FormaPagamento, BigDecimal> esperado = esperado(caixa);
        boolean diferenca = esperado.entrySet().stream().anyMatch(e -> contado.get(e.getKey()).compareTo(e.getValue()) != 0);
        String texto = motivo == null ? "" : motivo.trim();
        if (diferenca && (texto.length() < 5 || texto.length() > 500)) {
            throw new CampoInvalidoException("motivoDiferenca", "Explique a diferença (5 a 500 caracteres).");
        }
        caixa.fechar(esperado, contado, texto.isEmpty() ? null : texto, LocalDateTime.now(clock));
        Caixa salvo = caixas.saveAndFlush(caixa);
        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("esperado", soma(esperado));
        detalhes.put("contado", soma(contado));
        detalhes.put("diferenca", salvo.getDiferencaTotal());
        detalhes.put("diferencaDinheiro", salvo.diferenca().get(FormaPagamento.DINHEIRO));
        auditoria.registrar("CAIXA_FECHADO", ENTIDADE, salvo.getId(), detalhes);
        return resposta(salvo);
    }

    // ------------------------------------------------------------------ consultas

    @Transactional(readOnly = true)
    public Page<CaixaResponse> listar(StatusCaixa status, Long usuarioId, LocalDate inicio, LocalDate fim, Pageable pageable) {
        if (inicio != null && fim != null && fim.isBefore(inicio)) {
            throw new CampoInvalidoException("dataFim", "A data final não pode ser anterior à inicial.");
        }
        return caixas.buscar(status, usuarioId, inicio == null ? null : inicio.atStartOfDay(),
                fim == null ? null : fim.plusDays(1).atStartOfDay(), pageable).map(c -> CaixaResponse.of(c, null, null));
    }

    /** ADMIN vê qualquer caixa; os demais, só o próprio (403). */
    @Transactional(readOnly = true)
    public CaixaResponse detalhe(Long id, Long usuarioId, boolean admin) {
        Caixa caixa = caixas.findWithUsuarioById(id).orElseThrow(() -> new NotFoundException("Caixa", id));
        if (!admin && !caixa.getUsuario().getId().equals(usuarioId)) {
            throw new AccessDeniedException("Caixa de outro usuário");
        }
        return resposta(caixa);
    }

    // ------------------------------------------------------------------ apuração

    /** Esperado por forma: fundo (só dinheiro) + Σ(ENTRADA − SAIDA) dos lançamentos do caixa, agregado no banco. */
    Map<FormaPagamento, BigDecimal> esperado(Caixa caixa) {
        Map<FormaPagamento, BigDecimal> e = new EnumMap<>(FormaPagamento.class);
        for (FormaPagamento f : FormaPagamento.values()) {
            e.put(f, BigDecimal.ZERO.setScale(2));
        }
        e.put(FormaPagamento.DINHEIRO, dinheiro(caixa.getFundoAbertura()));
        if (caixa.getId() != null) {
            for (Object[] r : lancamentos.saldoPorFormaDoCaixa(caixa.getId())) {
                if (r[0] != null) {
                    FormaPagamento f = FormaPagamento.valueOf(r[0].toString());
                    e.put(f, e.get(f).add(FinanceiroService.decimal(r[1])));
                }
            }
        }
        return e;
    }

    private CaixaResponse resposta(Caixa caixa) {
        return CaixaResponse.of(caixa, caixa.isAberto() ? esperado(caixa) : null,
                movimentos.findByCaixaIdOrderByIdAsc(caixa.getId()).stream().map(CaixaResponse.Movimento::of).toList());
    }

    private static BigDecimal soma(Map<FormaPagamento, BigDecimal> m) {
        return m.values().stream().reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private static BigDecimal dinheiro(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    private Usuario usuario(Long usuarioId) {
        return usuarios.findById(usuarioId).orElseThrow(() -> new NotFoundException("Usuário", usuarioId));
    }
}
