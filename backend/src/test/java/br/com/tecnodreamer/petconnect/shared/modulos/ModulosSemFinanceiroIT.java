package br.com.tecnodreamer.petconnect.shared.modulos;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Instalação com {@code produtos, estoque, caixa, pdv, relatorios} e SEM {@code financeiro} gerencial. O PDV mantém o registro obrigatório
 * da venda no núcleo (lançamento e caixa); as APIs financeiras privadas somem; e os relatórios cuja origem não foi contratada (contas,
 * despesas, serviços) ficam indisponíveis também na exportação CSV, sem vazar dado do módulo desligado.
 */
@TestPropertySource(properties = "petconnect.modulos=produtos,estoque,caixa,pdv,relatorios")
class ModulosSemFinanceiroIT extends ModulosIntegrationTest {

    @Test
    void pdvMantemORegistroObrigatorioDaVendaNoNucleoMasAApiFinanceiraFicaIndisponivel() throws Exception {
        long p = produto("10.00", 0);
        semearSaldo(p, 3);
        long antes = jdbc.queryForObject("select count(*) from movimentacao_financeira", Long.class);

        long venda = venderOk(admin(), vendaJson("PIX", "0.00", "null", p, 1));

        assertThat(jdbc.queryForObject("select count(*) from movimentacao_financeira", Long.class)).isEqualTo(antes + 1);
        assertThat(jdbc.queryForObject("select caixa_id from venda where id = ?", Long.class, venda)).isNotNull();
        for (String token : new String[] {admin(), vendas()}) {
            exigirIndisponivel(consultar("/api/v1/financeiro/lancamentos", token));
            exigirIndisponivel(consultar("/api/v1/financeiro/fluxo", token));
            exigirIndisponivel(consultar("/api/v1/financeiro/resumo-dia", token));
            exigirIndisponivel(consultar("/api/v1/financeiro/contas-a-pagar", token));
            exigirIndisponivel(mvc.perform(post("/api/v1/financeiro/lancamentos").with(bearer(token)).contentType(MediaType.APPLICATION_JSON).content("{}")));
        }
        // O caixa (módulo contratado) funciona e enxerga a venda.
        consultar("/api/v1/caixas/resumo-atual", admin()).andExpect(status().isOk()).andExpect(jsonPath("$.aberto").value(true));
    }

    @Test
    void relatoriosDeOrigemDesligadaFicamIndisponiveisTambemNaExportacao() throws Exception {
        String admin = admin();
        for (String tipo : new String[] {"contas", "despesas", "servicos"}) {
            exigirIndisponivel(consultar("/api/v1/relatorios/" + tipo, admin));
            exigirIndisponivel(consultar("/api/v1/relatorios/" + tipo + "/csv", admin));
        }
        // Os de origem contratada seguem normais, inclusive CSV (VENDAS vê vendas; ADMIN vê os demais).
        for (String tipo : new String[] {"vendas", "produtos-mais-vendidos", "curva-abc", "estoque", "margem", "caixas"}) {
            int s = mvc.perform(get("/api/v1/relatorios/" + tipo).param("dataInicio", hoje().toString()).param("dataFim", hoje().toString()).with(bearer(admin)))
                    .andReturn().getResponse().getStatus();
            assertThat(s).as("relatório " + tipo).isEqualTo(200);
        }
        mvc.perform(get("/api/v1/relatorios/vendas/csv").param("dataInicio", hoje().toString()).param("dataFim", hoje().toString()).with(bearer(vendas())))
                .andExpect(status().isOk());
        consultar("/api/v1/modulos", admin).andExpect(jsonPath("$.relatorios.length()").value(6));
    }

    @Test
    void aListaDeRelatoriosDasCapacidadesNaoPublicaOsDesligados() throws Exception {
        String corpo = consultar("/api/v1/modulos", admin()).andReturn().getResponse().getContentAsString();
        assertThat(corpo).contains("vendas", "estoque", "caixas").doesNotContain("contas", "despesas", "servicos");
    }
}
