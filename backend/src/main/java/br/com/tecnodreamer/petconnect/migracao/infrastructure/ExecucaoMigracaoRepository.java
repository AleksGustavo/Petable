package br.com.tecnodreamer.petconnect.migracao.infrastructure;

import br.com.tecnodreamer.petconnect.migracao.application.ResultadoEntidade;
import br.com.tecnodreamer.petconnect.migracao.application.ResultadoMigracao;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

/**
 * Histórico das execuções (V10). Grava em transação PRÓPRIA (REQUIRES_NEW): o registro
 * sobrevive ao rollback do dry-run e a uma falha no meio da execução real.
 */
@Component
public class ExecucaoMigracaoRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate nova;

    public ExecucaoMigracaoRepository(NamedParameterJdbcTemplate jdbc, PlatformTransactionManager tm) {
        this.jdbc = jdbc;
        this.nova = new TransactionTemplate(tm);
        this.nova.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Long iniciar(boolean dryRun, LocalDateTime inicio) {
        return nova.execute(s -> {
            GeneratedKeyHolder chave = new GeneratedKeyHolder();
            jdbc.update("""
                    INSERT INTO migracao_legado_execucao (iniciada_em, modo, status)
                    VALUES (:inicio, :modo, 'EM_ANDAMENTO')
                    """, new MapSqlParameterSource("inicio", inicio).addValue("modo", dryRun ? "DRY_RUN" : "REAL"),
                    chave, new String[] {"id"});
            return chave.getKey().longValue();
        });
    }

    public void finalizar(ResultadoMigracao r) {
        nova.executeWithoutResult(s -> jdbc.update("""
                UPDATE migracao_legado_execucao
                   SET finalizada_em = :fim, status = :status, total_lidos = :lidos, total_migrados = :migrados,
                       total_ajustados = :ajustados, total_ignorados = :ignorados, total_ja_migrados = :ja,
                       total_pendencias = :pendencias, mensagem = :mensagem
                 WHERE id = :id
                """, new MapSqlParameterSource("id", r.execucaoId())
                .addValue("fim", r.fim())
                .addValue("status", r.status().name())
                .addValue("lidos", r.total(ResultadoEntidade::lidos))
                .addValue("migrados", r.total(ResultadoEntidade::migrados))
                .addValue("ajustados", r.total(ResultadoEntidade::ajustados))
                .addValue("ignorados", r.total(ResultadoEntidade::ignorados))
                .addValue("ja", r.total(ResultadoEntidade::jaMigrados))
                .addValue("pendencias", (long) r.pendencias().size())
                .addValue("mensagem", r.mensagem())));
    }
}
