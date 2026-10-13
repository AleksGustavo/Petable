package br.com.tecnodreamer.petconnect.migracao;

import br.com.tecnodreamer.petconnect.migracao.application.ResultadoEntidade;
import br.com.tecnodreamer.petconnect.migracao.application.ResultadoMigracao;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MigracaoLegadoIdempotenciaIT extends MigracaoIntegrationTest {

    @Test
    void segundaExecucaoNaoDeveCriarNovosRegistros() {
        assertThat(cenario.depoisRepeticao()).isEqualTo(cenario.antesRepeticao());
        assertThat(cenario.repeticao().status()).isEqualTo(ResultadoMigracao.Status.CONCLUIDA);
    }

    @Test
    void segundaExecucaoDeveMarcarJaMigrados() {
        ResultadoMigracao r = cenario.repeticao();
        assertThat(r.total(ResultadoEntidade::migrados) + r.total(ResultadoEntidade::ajustados)
                + r.total(ResultadoEntidade::ignorados)).isZero();
        assertThat(r.total(ResultadoEntidade::jaMigrados)).isEqualTo(r.total(ResultadoEntidade::lidos)).isPositive();
    }

    @Test
    void naoDeveDuplicarEstoque() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimentacao_estoque WHERE produto_id IN (?, ?) AND tipo = 'AJUSTE'",
                Long.class, idNovo("produto", 1), idNovo("produto", 4))).isEqualTo(2L);
    }

    @Test
    void naoDeveDuplicarFinanceiro() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimentacao_financeira WHERE chave_origem IN (?, ?)", Long.class,
                "VENDA:" + idNovo("venda", 1), "VENDA:" + idNovo("venda", 4))).isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimentacao_financeira WHERE chave_origem IN "
                + "('LEGADO:3', 'LEGADO:4', 'LEGADO:5')", Long.class)).isEqualTo(3L);
    }
}
