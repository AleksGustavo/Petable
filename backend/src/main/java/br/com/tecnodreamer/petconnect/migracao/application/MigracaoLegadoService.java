package br.com.tecnodreamer.petconnect.migracao.application;

import br.com.tecnodreamer.petconnect.migracao.infrastructure.DestinoMigracao;
import br.com.tecnodreamer.petconnect.migracao.infrastructure.ExecucaoMigracaoRepository;
import br.com.tecnodreamer.petconnect.migracao.infrastructure.MapaMigracao;
import br.com.tecnodreamer.petconnect.financeiro.application.FinanceiroBackfill;
import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Orquestra a migração do legado (§9, §10).
 * <ul>
 *   <li>Ordem fixa dos {@link Migrador}es (por {@code @Order}), lotes por id (sem OFFSET).</li>
 *   <li>REAL: uma transação por lote; falha interrompe as entidades seguintes e a próxima
 *       execução retoma pelo mapa (UNIQUE entidade + id_legado).</li>
 *   <li>DRY-RUN: tudo numa transação que termina em rollback (inclusive o mapa); sem auditoria.</li>
 *   <li>Auditoria por registro suprimida; um único {@code MIGRACAO_LEGADO_EXECUTADA} na execução real.</li>
 * </ul>
 * Nada aqui loga dado pessoal: só entidade, id legado, códigos e contagens.
 */
@Service
public class MigracaoLegadoService {

    private static final Logger log = LoggerFactory.getLogger(MigracaoLegadoService.class);
    static final String USUARIO_TECNICO = "migracao.legado";

    private final List<Migrador> migradores;
    private final MapaMigracao mapa;
    private final ExecucaoMigracaoRepository execucoes;
    private final DestinoMigracao destino;
    private final PasswordEncoder encoder;
    private final FinanceiroBackfill backfill;
    private final MigracaoConferenciaService conferencias;
    private final MigracaoRelatorioService relatorios;
    private final AuditoriaService auditoria;
    private final PlatformTransactionManager tm;
    private final Clock clock;

    @PersistenceContext
    private EntityManager em;

    public MigracaoLegadoService(List<Migrador> migradores, MapaMigracao mapa, ExecucaoMigracaoRepository execucoes,
            DestinoMigracao destino, PasswordEncoder encoder, FinanceiroBackfill backfill,
            MigracaoConferenciaService conferencias, MigracaoRelatorioService relatorios, AuditoriaService auditoria,
            PlatformTransactionManager tm, Clock clock) {
        this.migradores = List.copyOf(migradores);
        this.mapa = mapa;
        this.execucoes = execucoes;
        this.destino = destino;
        this.encoder = encoder;
        this.backfill = backfill;
        this.conferencias = conferencias;
        this.relatorios = relatorios;
        this.auditoria = auditoria;
        this.tm = tm;
        this.clock = clock;
    }

    /** Entidades na ordem de execução (usado pelo relatório e pelos testes). */
    public List<String> ordem() {
        return migradores.stream().map(Migrador::entidade).toList();
    }

    public synchronized ResultadoMigracao executar(OpcoesMigracao opcoes, NamedParameterJdbcTemplate legado) {
        return auditoria.semAuditoriaPorRegistro(() -> rodar(opcoes, legado));
    }

    private ResultadoMigracao rodar(OpcoesMigracao o, NamedParameterJdbcTemplate legado) {
        LocalDateTime inicio = LocalDateTime.now(clock);
        Long execucao = execucoes.iniciar(o.dryRun(), inicio);
        ResultadoMigracao r = new ResultadoMigracao(execucao, o.dryRun(), inicio);
        String[] atual = {"preparacao", null};
        log.info("Migração do legado #{} iniciada ({}).", execucao, o.dryRun() ? "DRY-RUN" : "REAL");
        mapa.limpar();
        TransactionTemplate tx = new TransactionTemplate(tm);
        ResultadoMigracao.Status status;
        String mensagem = null;
        try {
            if (o.dryRun()) {
                tx.executeWithoutResult(s -> {
                    s.setRollbackOnly();
                    etapas(o, legado, r, tx, execucao, atual);
                });
            } else {
                etapas(o, legado, r, tx, execucao, atual);
            }
            status = r.conferenciasOk() ? ResultadoMigracao.Status.CONCLUIDA : ResultadoMigracao.Status.FALHOU;
            if (status == ResultadoMigracao.Status.FALHOU) {
                mensagem = "Conferência automática falhou.";
            }
        } catch (RuntimeException e) {
            status = ResultadoMigracao.Status.FALHOU;
            // Só o tipo da exceção: a mensagem de erro do banco pode conter documento/e-mail.
            mensagem = "Falha em " + atual[0] + (atual[1] == null ? "" : " legado #" + atual[1]) + ": "
                    + e.getClass().getSimpleName();
            log.error("Migração #{} interrompida. {}", execucao, mensagem);
        } finally {
            mapa.limpar();
        }
        r.finalizar(status, mensagem, LocalDateTime.now(clock));
        r.relatorio(relatorios.escrever(r, o));
        execucoes.finalizar(r);
        if (!o.dryRun()) {
            auditoria.registrarSempre(USUARIO_TECNICO, "MIGRACAO_LEGADO_EXECUTADA", "migracao", execucao, resumo(r));
        }
        log.info("Migração #{} {}: lidos={}, migrados={}, ajustados={}, ignorados={}, jaMigrados={}, "
                + "pendencias={}. Relatório: {}", execucao, status, r.total(ResultadoEntidade::lidos),
                r.total(ResultadoEntidade::migrados), r.total(ResultadoEntidade::ajustados),
                r.total(ResultadoEntidade::ignorados), r.total(ResultadoEntidade::jaMigrados), r.pendencias().size(),
                r.relatorio());
        return r;
    }

    private void etapas(OpcoesMigracao o, NamedParameterJdbcTemplate legado, ResultadoMigracao r, TransactionTemplate tx,
            Long execucao, String[] atual) {
        Long tecnico = tx.execute(s -> usuarioTecnico());
        Contexto ctx = new ContextoExecucao(o, legado, tecnico);
        for (Migrador m : migradores) {
            atual[0] = m.entidade();
            atual[1] = null;
            migrarEntidade(m, o, legado, r, tx, ctx, execucao, atual);
        }
        atual[0] = "backfill financeiro";
        atual[1] = null;
        tx.executeWithoutResult(s -> {
            FinanceiroBackfill.Resultado b = backfill.executar(o.formaServicoBackfill());
            r.backfill(b);
            if (b.servicosPendentes() > 0) {
                r.pendencia(new Pendencia("servico_realizado", null,
                        "SERVICOS_SEM_FORMA_PAGAMENTO (" + b.servicosPendentes() + ")"));
            }
            em.flush();
            atual[0] = "conferencias";
            r.conferencias(conferencias.conferir(r, o, legado));
        });
    }

    private void migrarEntidade(Migrador m, OpcoesMigracao o, NamedParameterJdbcTemplate legado, ResultadoMigracao r,
            TransactionTemplate tx, Contexto ctx, Long execucao, String[] atual) {
        ResultadoEntidade total = r.entidade(m.entidade());
        long ultimo = 0;
        while (true) {
            List<Linha> lote = legado.queryForList(m.consulta(), Map.of("ultimo", ultimo, "lote", o.lote())).stream()
                    .map(Linha::new).toList();
            if (lote.isEmpty()) {
                return;
            }
            List<Situacao> situacoes = new ArrayList<>();
            List<Pendencia> pendencias = new ArrayList<>();
            int[] jaMigrados = {0};
            try {
                tx.executeWithoutResult(s -> {
                    for (Linha l : lote) {
                        atual[1] = String.valueOf(l.id());
                        if (mapa.buscar(m.entidade(), l.id()).isPresent()) {
                            jaMigrados[0]++;
                            continue;
                        }
                        Desfecho d = m.migrar(l, ctx);
                        mapa.gravar(m.entidade(), l.id(), d.idNovo(), d.situacao(), motivo(d), execucao);
                        situacoes.add(d.situacao());
                        d.pendencias().forEach(p -> pendencias.add(new Pendencia(m.entidade(), l.id(), p)));
                    }
                    em.flush();
                    em.clear();
                });
            } catch (RuntimeException e) {
                mapa.descartar(m.entidade());
                throw e;
            }
            // Só conta o que o lote confirmou (na execução real, um lote desfeito não entra).
            situacoes.forEach(total::contar);
            for (int i = 0; i < jaMigrados[0]; i++) {
                total.contarJaMigrado();
            }
            pendencias.forEach(r::pendencia);
            ultimo = lote.get(lote.size() - 1).id();
            if (lote.size() < o.lote()) {
                return;
            }
        }
    }

    private Long usuarioTecnico() {
        return destino.usuarioPorUsername(USUARIO_TECNICO).orElseGet(() -> {
            byte[] b = new byte[32];
            new SecureRandom().nextBytes(b);
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("username", USUARIO_TECNICO);
            v.put("email", USUARIO_TECNICO + "@migracao.invalid");
            v.put("senha_hash", encoder.encode(Base64.getEncoder().encodeToString(b)));
            v.put("papel_id", destino.papelId("VENDAS").orElseThrow());
            v.put("ativo", false);
            return destino.inserir("usuario", v);
        });
    }

    private static String motivo(Desfecho d) {
        if (d.pendencias().isEmpty()) {
            return null;
        }
        String s = String.join(",", d.pendencias());
        return s.length() <= 500 ? s : s.substring(0, 500);
    }

    private static Map<String, Object> resumo(ResultadoMigracao r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("execucaoId", r.execucaoId());
        m.put("status", r.status().name());
        m.put("lidos", r.total(ResultadoEntidade::lidos));
        m.put("migrados", r.total(ResultadoEntidade::migrados));
        m.put("ajustados", r.total(ResultadoEntidade::ajustados));
        m.put("ignorados", r.total(ResultadoEntidade::ignorados));
        m.put("jaMigrados", r.total(ResultadoEntidade::jaMigrados));
        m.put("pendencias", r.pendencias().size());
        return m;
    }

    private final class ContextoExecucao implements Contexto {

        private final OpcoesMigracao opcoes;
        private final NamedParameterJdbcTemplate legado;
        private final Long tecnico;

        ContextoExecucao(OpcoesMigracao opcoes, NamedParameterJdbcTemplate legado, Long tecnico) {
            this.opcoes = opcoes;
            this.legado = legado;
            this.tecnico = tecnico;
        }

        @Override
        public Optional<Long> idNovo(String entidade, Long idLegado) {
            if (idLegado == null) {
                return Optional.empty();
            }
            return mapa.buscar(entidade, idLegado).map(MapaMigracao.Registro::idNovo);
        }

        @Override
        public List<Linha> consultarLegado(String sql, Map<String, ?> parametros) {
            return legado.queryForList(sql, parametros).stream().map(Linha::new).toList();
        }

        @Override
        public OpcoesMigracao opcoes() {
            return opcoes;
        }

        @Override
        public Long usuarioTecnicoId() {
            return tecnico;
        }
    }
}
