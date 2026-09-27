package br.com.tecnodreamer.petconnect.migracao;

import br.com.tecnodreamer.petconnect.migracao.application.ResultadoEntidade;
import br.com.tecnodreamer.petconnect.migracao.application.ResultadoMigracao;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MigracaoLegadoRetomadaIT extends MigracaoIntegrationTest {

    @Test
    void falhaNoMeioDeveInterromperEntidadesSeguintes() {
        ResultadoMigracao f = cenario.falha();
        assertThat(f.status()).isEqualTo(ResultadoMigracao.Status.FALHOU);
        assertThat(f.mensagem()).startsWith("Falha em venda").doesNotContain("SELECT");
        assertThat(f.entidades()).extracting(ResultadoEntidade::entidade).doesNotContain("movimentacao_financeira");
        assertThat(f.conferencias()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT status FROM migracao_legado_execucao WHERE id = ?", String.class,
                f.execucaoId())).isEqualTo("FALHOU");
    }

    @Test
    void reexecucaoDeveContinuarDosNaoProcessados() {
        ResultadoMigracao r = cenario.real();
        assertThat(r.daEntidade("cliente").jaMigrados()).isEqualTo(4);
        assertThat(r.daEntidade("produto").jaMigrados()).isEqualTo(4);
        assertThat(r.daEntidade("venda").lidos()).isEqualTo(4);
        assertThat(r.daEntidade("venda").jaMigrados()).isZero();
        assertThat(r.daEntidade("movimentacao_financeira").lidos()).isEqualTo(5);
    }

    @Test
    void lotesConfirmadosAntesDaFalhaDevemPermanecerNaExecucaoReal() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM migracao_legado_mapa WHERE execucao_id = ?", Long.class,
                cenario.falha().execucaoId())).isGreaterThan(40L);
        assertThat(jdbc.queryForObject("SELECT execucao_id FROM migracao_legado_mapa WHERE entidade = 'produto' "
                + "AND id_legado = 1", Long.class)).isEqualTo(cenario.falha().execucaoId());
    }
}
