package br.com.tecnodreamer.petconnect.financeiro.web;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CaixaControllerIT extends FinanceiroIntegrationTest {

    @Test
    void adminDeveAbrirCaixa() throws Exception {
        String token = operador("ADMIN", false);

        abrir(token, "200.00").andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ABERTO"))
                .andExpect(jsonPath("$.fundoAbertura").value(200.0))
                .andExpect(jsonPath("$.esperado.DINHEIRO").value(200.0));
        mvc.perform(get(CAIXAS).with(bearer(token))).andExpect(status().isOk());
        mvc.perform(get(CAIXAS + "/resumo-atual").with(bearer(token)))
                .andExpect(jsonPath("$.aberto").value(true))
                .andExpect(jsonPath("$.saldoEsperadoDinheiro").value(200.0));
    }

    @Test
    void vendasDeveAbrirProprioCaixa() throws Exception {
        String token = operador("VENDAS", false);

        String body = abrir(token, "50").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        mvc.perform(get(CAIXAS + "/" + id).with(bearer(token))).andExpect(status().isOk());
        mvc.perform(get(CAIXAS).with(bearer(token))).andExpect(status().isForbidden());
        // Caixa de outro usuário: 403.
        mvc.perform(get(CAIXAS + "/" + id).with(bearer(operador("VENDAS", false)))).andExpect(status().isForbidden());
    }

    @Test
    void servicoDeveAbrirProprioCaixa() throws Exception {
        String token = operador("SERVICO", false);

        abrir(token, "0").andExpect(status().isCreated());
        mvc.perform(get(CAIXAS + "/atual").with(bearer(token))).andExpect(status().isOk());
        mvc.perform(get(FIN + "/resumo-dia").with(bearer(token))).andExpect(status().isForbidden());
    }

    @Test
    void segundoCaixaDeveRetornar409() throws Exception {
        String token = operador("VENDAS", true);

        abrir(token, "10").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Já existe um caixa aberto para o usuário atual."));
    }

    @Test
    void deveRegistrarSangria() throws Exception {
        String token = operador("VENDAS", false);
        abrir(token, "200.00").andExpect(status().isCreated());

        movimento(token, "sangrias", "50.00", "Retirada preventiva").andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("SANGRIA"));
        assertThat(esperadoDinheiro(token)).isEqualTo("150.00");
        movimento(token, "sangrias", "10.00", "curt").andExpect(status().isBadRequest());
    }

    @Test
    void deveRegistrarSuprimento() throws Exception {
        String token = operador("VENDAS", false);
        abrir(token, "200.00").andExpect(status().isCreated());

        movimento(token, "suprimentos", "100.00", "Reforço de troco").andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("SUPRIMENTO"));
        assertThat(esperadoDinheiro(token)).isEqualTo("300.00");
    }

    @Test
    void sangriaExcedenteDeveRetornar409() throws Exception {
        String token = operador("VENDAS", false);
        abrir(token, "100.00").andExpect(status().isCreated());

        movimento(token, "sangrias", "100.01", "Retirada excessiva").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A sangria não pode exceder o saldo esperado em dinheiro."));
        assertThat(esperadoDinheiro(token)).isEqualTo("100.00");
    }

    @Test
    void deveFecharComDiferencaZero() throws Exception {
        String token = operador("VENDAS", false);
        abrir(token, "80.00").andExpect(status().isCreated());

        fechar(token, contadoJson("80.00", null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FECHADO"))
                .andExpect(jsonPath("$.diferencaTotal").value(0.0))
                .andExpect(jsonPath("$.esperado.DINHEIRO").value(80.0));
        mvc.perform(get(CAIXAS + "/resumo-atual").with(bearer(token))).andExpect(jsonPath("$.aberto").value(false));
    }

    @Test
    void deveExigirMotivoComDiferenca() throws Exception {
        String token = operador("VENDAS", false);
        abrir(token, "80.00").andExpect(status().isCreated());

        fechar(token, contadoJson("75.00", null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.motivoDiferenca").exists());
        fechar(token, contadoJson("75.00", "Faltou uma moeda de R$ 5")).andExpect(status().isOk())
                .andExpect(jsonPath("$.diferenca.DINHEIRO").value(-5.0))
                .andExpect(jsonPath("$.diferencaTotal").value(-5.0));
    }

    @Test
    void caixaFechadoNaoPodeFecharNovamente() throws Exception {
        String token = operador("SERVICO", false);
        abrir(token, "0").andExpect(status().isCreated());
        fechar(token, contadoJson("0", null)).andExpect(status().isOk());

        fechar(token, contadoJson("0", null)).andExpect(status().isConflict());
        // Não existe reabertura: abrir de novo cria OUTRA sessão.
        abrir(token, "0").andExpect(status().isCreated());
    }
}
