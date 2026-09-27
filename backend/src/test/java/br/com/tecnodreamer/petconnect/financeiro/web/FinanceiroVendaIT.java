package br.com.tecnodreamer.petconnect.financeiro.web;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FinanceiroVendaIT extends FinanceiroIntegrationTest {

    @Test
    void vendaConcluidaDeveGerarExatamenteUmLancamento() throws Exception {
        long p = produto("30.00", 5);
        String token = vendas();

        String body = vender(token, UUID.randomUUID().toString(), vendaJson("PIX", "0", "null", p, 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caixaId").isNumber())
                .andExpect(jsonPath("$.lancamentoFinanceiroId").isNumber())
                .andReturn().getResponse().getContentAsString();
        long venda = ((Number) JsonPath.read(body, "$.id")).longValue();
        long caixa = ((Number) JsonPath.read(body, "$.caixaId")).longValue();

        assertThat(lancamentos("venda_id = ?", venda)).isEqualTo(1);
        var l = jdbc.queryForMap("select tipo, categoria, valor, forma_pagamento, caixa_id, chave_origem from movimentacao_financeira "
                + "where venda_id = ?", venda);
        assertThat(l).containsEntry("tipo", "ENTRADA").containsEntry("categoria", "VENDA_PRODUTO")
                .containsEntry("forma_pagamento", "PIX").containsEntry("chave_origem", "VENDA:" + venda);
        assertThat(((Number) l.get("caixa_id")).longValue()).isEqualTo(caixa);
        assertThat(new java.math.BigDecimal(l.get("valor").toString())).isEqualByComparingTo("60.00");
    }

    @Test
    void replayNaoDeveDuplicarLancamento() throws Exception {
        long p = produto("10.00", 5);
        String token = vendas();
        String chave = UUID.randomUUID().toString();
        String json = vendaJson("PIX", "0", "null", p, 1);

        String body = vender(token, chave, json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long venda = ((Number) JsonPath.read(body, "$.id")).longValue();
        vender(token, chave, json).andExpect(status().isOk()).andExpect(jsonPath("$.lancamentoFinanceiroId").isNumber());

        assertThat(lancamentos("venda_id = ?", venda)).isEqualTo(1);
        assertThat(saldo(p)).isEqualTo(4);
    }

    @Test
    void vendaSemCaixaDeveRetornar409SemBaixarEstoque() throws Exception {
        long p = produto("10.00", 5);
        String semCaixa = operador("VENDAS", false);
        int vendasAntes = jdbc.queryForObject("select count(*) from venda", Integer.class);

        vender(semCaixa, UUID.randomUUID().toString(), vendaJson("PIX", "0", "null", p, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Não há caixa aberto para o operador atual. Abra o caixa antes de continuar."));

        assertThat(saldo(p)).isEqualTo(5);
        assertThat(jdbc.queryForObject("select count(*) from venda", Integer.class)).isEqualTo(vendasAntes);
    }

    @Test
    void cancelamentoDeveGerarEstornoVinculado() throws Exception {
        long p = produto("25.00", 5);
        String token = admin();
        long venda = venderOk(token, vendaJson("DINHEIRO", "0", "50.00", p, 1));

        cancelar(token, venda, "Cliente desistiu").andExpect(status().isOk());

        Long entrada = jdbc.queryForObject("select id from movimentacao_financeira where chave_origem = ?", Long.class, "VENDA:" + venda);
        var estorno = jdbc.queryForMap("select tipo, categoria, valor, forma_pagamento, estorno_de_id, motivo_estorno "
                + "from movimentacao_financeira where chave_origem = ?", "VENDA_ESTORNO:" + venda);
        assertThat(estorno).containsEntry("tipo", "SAIDA").containsEntry("categoria", "VENDA_PRODUTO")
                .containsEntry("forma_pagamento", "DINHEIRO").containsEntry("motivo_estorno", "Cliente desistiu");
        assertThat(((Number) estorno.get("estorno_de_id")).longValue()).isEqualTo(entrada);
        assertThat(saldo(p)).isEqualTo(5);
    }

    @Test
    void cancelamentoSemCaixaDeveFazerRollback() throws Exception {
        long p = produto("25.00", 5);
        long venda = venderOk(admin(), vendaJson("PIX", "0", "null", p, 2));
        String adminSemCaixa = operador("ADMIN", false);

        cancelar(adminSemCaixa, venda, "Tentativa sem caixa").andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("select status from venda where id = ?", String.class, venda)).isEqualTo("CONCLUIDA");
        assertThat(saldo(p)).isEqualTo(3);
        assertThat(lancamentos("chave_origem = ?", "VENDA_ESTORNO:" + venda)).isZero();
        assertThat(movimentacoes(p, "ESTORNO_VENDA")).isZero();
    }
}
