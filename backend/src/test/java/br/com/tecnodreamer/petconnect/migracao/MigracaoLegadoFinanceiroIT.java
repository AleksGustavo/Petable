package br.com.tecnodreamer.petconnect.migracao;

import br.com.tecnodreamer.petconnect.migracao.application.ResultadoEntidade;
import br.com.tecnodreamer.petconnect.migracao.application.ResultadoMigracao;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MigracaoLegadoFinanceiroIT extends MigracaoIntegrationTest {

    @Test
    void backfillDeveGerarLancamentoDasVendasMigradas() {
        for (long legado : new long[] {1, 4}) {
            assertThat(jdbc.queryForObject("SELECT categoria FROM movimentacao_financeira WHERE chave_origem = ?", String.class,
                    "VENDA:" + idNovo("venda", legado))).isEqualTo("VENDA_PRODUTO");
        }
    }

    @Test
    void financeiroLegadoDeVendaDeveSerIgnorado() {
        assertThat(motivo("movimentacao_financeira", 1)).isEqualTo("REGENERADO_PELO_BACKFILL");
        assertThat(motivo("movimentacao_financeira", 2)).isEqualTo("REGENERADO_PELO_BACKFILL");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimentacao_financeira WHERE chave_origem LIKE 'LEGADO:%' "
                + "AND categoria IN ('VENDA_PRODUTO', 'SERVICO')", Long.class)).isZero();
    }

    @Test
    void despesaManualLegadaDeveSerMigradaUmaVez() {
        Map<String, Object> l = jdbc.queryForMap("SELECT tipo, categoria, valor, caixa_id FROM movimentacao_financeira "
                + "WHERE chave_origem = 'LEGADO:3'");
        assertThat(l).containsEntry("tipo", "SAIDA").containsEntry("categoria", "DESPESA_OPERACIONAL");
        assertThat((BigDecimal) l.get("valor")).isEqualByComparingTo("150.00");
        assertThat(l.get("caixa_id")).isNull();
        assertThat(jdbc.queryForObject("SELECT categoria FROM movimentacao_financeira WHERE chave_origem = 'LEGADO:4'",
                String.class)).isEqualTo("OUTROS");
    }

    @Test
    void somaFinanceiraDeVendasDeveConferir() throws Exception {
        BigDecimal soma = jdbc.queryForObject("SELECT SUM(valor) FROM movimentacao_financeira WHERE chave_origem IN (?, ?)",
                BigDecimal.class, "VENDA:" + idNovo("venda", 1), "VENDA:" + idNovo("venda", 4));
        assertThat(soma).isEqualByComparingTo("276.00");
        // Relatório da etapa 07 no período histórico fecha com as vendas migradas.
        String token = login(ADMIN_USER, ADMIN_PASSWORD);
        mvc.perform(get("/api/v1/relatorios/vendas?dataInicio=2019-03-01&dataFim=2019-03-31").with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantidade").value(2))
                .andExpect(jsonPath("$.liquido").value(276.0));
    }
}
