package br.com.tecnodreamer.petconnect.financeiro.web;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FinanceiroControllerIT extends FinanceiroIntegrationTest {

    @Test
    void adminDeveListarLancamentos() throws Exception {
        long p = produto("10.00", 5);
        venderOk(admin(), vendaJson("PIX", "0", "null", p, 1));

        mvc.perform(get(FIN + "/lancamentos").param("origem", "VENDA").param("dataInicio", HOJE.toString())
                        .param("dataFim", HOJE.toString()).with(comPapel("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].origem.tipo").exists())
                .andExpect(jsonPath("$.totalElements").isNumber());
        mvc.perform(get(FIN + "/lancamentos").param("origem", "XPTO").with(comPapel("ADMIN"))).andExpect(status().isBadRequest());
    }

    @Test
    void vendasDeveReceber403() throws Exception {
        mvc.perform(get(FIN + "/lancamentos").with(comPapel("VENDAS"))).andExpect(status().isForbidden());
        mvc.perform(post(FIN + "/lancamentos").with(comPapel("VENDAS")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get(FIN + "/contas-a-pagar").with(comPapel("VENDAS"))).andExpect(status().isForbidden());
    }

    @Test
    void servicoDeveReceber403() throws Exception {
        mvc.perform(get(FIN + "/fluxo").param("dataInicio", HOJE.toString()).param("dataFim", HOJE.toString())
                .with(comPapel("SERVICO"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/caixas").with(comPapel("SERVICO"))).andExpect(status().isForbidden());
    }

    @Test
    void adminDeveCriarManual() throws Exception {
        String chave = novaChave();

        lancarManual(admin(), chave, "SAIDA", "DESPESA_OPERACIONAL", "89.90")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoria").value("DESPESA_OPERACIONAL"))
                .andExpect(jsonPath("$.caixaId").isNumber())
                .andExpect(jsonPath("$.estornavel").value(true));
        assertThat(lancamentos("chave_origem = ?", "MANUAL:" + chave)).isEqualTo(1);
        lancarManual(admin(), novaChave(), "ENTRADA", "VENDA_PRODUTO", "10.00").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.categoria").exists());
        lancarManual(admin(), "sem-uuid", "SAIDA", "OUTROS", "10.00").andExpect(status().isBadRequest());
    }

    @Test
    void mesmaChaveNaoDeveDuplicarManual() throws Exception {
        String chave = novaChave();
        lancarManual(admin(), chave, "SAIDA", "OUTROS", "12.00").andExpect(status().isCreated());

        lancarManual(admin(), chave, "SAIDA", "OUTROS", "12.00")
                .andExpect(status().isOk()).andExpect(header().string("Idempotent-Replayed", "true"));
        assertThat(lancamentos("chave_origem = ?", "MANUAL:" + chave)).isEqualTo(1);
    }

    @Test
    void adminDeveEstornarManual() throws Exception {
        String body = lancarManual(admin(), novaChave(), "SAIDA", "DESPESA_FUNCIONARIO", "40.00")
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();

        mvc.perform(post(FIN + "/lancamentos/" + id + "/estornar").with(bearer(admin())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\": \"Lançado em duplicidade\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("ENTRADA"))
                .andExpect(jsonPath("$.estornoDeId").value(id));
        mvc.perform(post(FIN + "/lancamentos/" + id + "/estornar").with(bearer(admin())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\": \"De novo\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("Este lançamento já foi estornado."));
        mvc.perform(get(FIN + "/lancamentos/" + id).with(comPapel("ADMIN")))
                .andExpect(jsonPath("$.estornadoPorId").isNumber()).andExpect(jsonPath("$.estornavel").value(false));
        assertThat(acoesAuditadas("lancamento", id)).contains("LANCAMENTO_CRIADO", "LANCAMENTO_ESTORNADO");
    }

    @Test
    void fluxoDeveSomarEntradasSaidasSaldo() throws Exception {
        String hoje = HOJE.toString();
        String antes = mvc.perform(get(FIN + "/fluxo").param("dataInicio", hoje).param("dataFim", hoje).with(comPapel("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        lancarManual(admin(), novaChave(), "ENTRADA", "OUTROS", "100.00").andExpect(status().isCreated());
        lancarManual(admin(), novaChave(), "SAIDA", "DESPESA_OPERACIONAL", "30.00").andExpect(status().isCreated());

        String depois = mvc.perform(get(FIN + "/fluxo").param("dataInicio", hoje).param("dataFim", hoje).with(comPapel("ADMIN")))
                .andReturn().getResponse().getContentAsString();
        assertThat(delta(depois, antes, "$.entradas")).isEqualByComparingTo("100.00");
        assertThat(delta(depois, antes, "$.saidas")).isEqualByComparingTo("30.00");
        assertThat(delta(depois, antes, "$.saldo")).isEqualByComparingTo("70.00");
        mvc.perform(get(FIN + "/fluxo").param("dataInicio", hoje).param("dataFim", HOJE.minusDays(1).toString())
                .with(comPapel("ADMIN"))).andExpect(status().isBadRequest());
        mvc.perform(get(FIN + "/resumo-dia").with(comPapel("ADMIN"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.caixasAbertos").isNumber());
    }

    private static BigDecimal delta(String depois, String antes, String campo) {
        return new BigDecimal(JsonPath.read(depois, campo).toString()).subtract(new BigDecimal(JsonPath.read(antes, campo).toString()));
    }
}
