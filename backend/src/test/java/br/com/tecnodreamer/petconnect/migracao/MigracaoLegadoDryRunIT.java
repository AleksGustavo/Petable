package br.com.tecnodreamer.petconnect.migracao;

import br.com.tecnodreamer.petconnect.migracao.application.ResultadoEntidade;
import br.com.tecnodreamer.petconnect.migracao.application.ResultadoMigracao;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MigracaoLegadoDryRunIT extends MigracaoIntegrationTest {

    @Test
    void dryRunNaoDevePersistirDados() {
        assertThat(cenario.depoisDryRun()).isEqualTo(cenario.antesDryRun());
        // ... mas processou de verdade: as contagens mostram o que SERIA gravado.
        assertThat(cenario.dryRun().total(ResultadoEntidade::migrados) + cenario.dryRun().total(ResultadoEntidade::ajustados))
                .isGreaterThan(30);
        assertThat(cenario.dryRun().status()).isEqualTo(ResultadoMigracao.Status.CONCLUIDA);
        assertThat(cenario.dryRun().conferenciasOk()).isTrue();
    }

    @Test
    void dryRunNaoDevePersistirMapa() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM migracao_legado_mapa WHERE execucao_id = ?", Long.class,
                cenario.dryRun().execucaoId())).isZero();
        assertThat(jdbc.queryForObject("SELECT modo FROM migracao_legado_execucao WHERE id = ?", String.class,
                cenario.dryRun().execucaoId())).isEqualTo("DRY_RUN");
    }

    @Test
    void dryRunNaoDeveAuditar() {
        assertThat(cenario.auditoriaDepoisDryRun()).isEqualTo(cenario.auditoriaAntesDryRun());
    }

    @Test
    void dryRunDeveGerarRelatorio() throws Exception {
        String texto = Files.readString(cenario.dryRun().relatorio());
        assertThat(texto).contains("modo: DRY_RUN").contains("| cliente | 4 |").contains("## Conferências")
                .contains("- vendas: PASS");
        assertThat(cenario.dryRun().relatorio().getFileName().toString()).endsWith("-dry-run.md");
    }
}
