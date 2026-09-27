package br.com.tecnodreamer.petconnect.financeiro.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ContaPagarControllerIT extends FinanceiroIntegrationTest {

    private org.springframework.test.web.servlet.ResultActions pagar(long conta, String forma) throws Exception {
        return mvc.perform(post(FIN + "/contas-a-pagar/" + conta + "/pagar").with(bearer(admin()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"formaPagamento\": \"" + forma + "\"}"));
    }

    @Test
    void compraRecebidaDeveCriarConta() throws Exception {
        long conta = compraRecebida("10:125.00");

        mvc.perform(get(FIN + "/contas-a-pagar/" + conta).with(comPapel("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABERTA"))
                .andExpect(jsonPath("$.valorOriginal").value(1250.0))
                .andExpect(jsonPath("$.saldo").value(1250.0))
                .andExpect(jsonPath("$.dataVencimento").value(HOJE.plusDays(30).toString()))
                .andExpect(jsonPath("$.situacao").value("A_VENCER"));
        assertThat(lancamentos("conta_pagar_id = ?", conta)).isZero();
        mvc.perform(get(FIN + "/contas-a-pagar").param("situacao", "A_VENCER").with(comPapel("ADMIN"))).andExpect(status().isOk());
    }

    @Test
    void recebimentoSemVencimentoDeveRetornar400() throws Exception {
        long fornecedor = criarFornecedor(unico("Fornecedor Sem Vencimento"));
        long produtoId = produto("10.00", 0);
        long compra = criarCompra(fornecedor, String.valueOf(produtoId), "1:5.00");

        mvc.perform(post("/api/v1/compras/" + compra + "/receber").with(bearer(admin())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataEntrega\": \"" + HOJE + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.dataVencimento").exists());
        mvc.perform(post("/api/v1/compras/" + compra + "/receber").with(bearer(admin())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataEntrega\": \"" + HOJE + "\", \"dataVencimento\": \"" + HOJE.plusDays(400) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.dataVencimento").exists());
        assertThat(jdbc.queryForObject("select status from compra where id = ?", String.class, compra)).isEqualTo("PEDIDO");
    }

    @Test
    void adminDevePagarConta() throws Exception {
        long conta = compraRecebida("2:50.00");

        pagar(conta, "PIX")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAGA"))
                .andExpect(jsonPath("$.saldo").value(0.0))
                .andExpect(jsonPath("$.formaPagamento").value("PIX"))
                .andExpect(jsonPath("$.lancamentoId").isNumber());
        mvc.perform(post(FIN + "/contas-a-pagar/" + conta + "/pagar").with(comPapel("VENDAS"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
    }

    @Test
    void pagamentoDeveGerarSaida() throws Exception {
        long conta = compraRecebida("3:40.00");
        pagar(conta, "TRANSFERENCIA").andExpect(status().isOk());

        var saida = jdbc.queryForMap("select tipo, categoria, valor, forma_pagamento, compra_id from movimentacao_financeira "
                + "where conta_pagar_id = ?", conta);
        assertThat(saida).containsEntry("tipo", "SAIDA").containsEntry("categoria", "COMPRA_MERCADORIA")
                .containsEntry("forma_pagamento", "TRANSFERENCIA");
        assertThat(new java.math.BigDecimal(saida.get("valor").toString())).isEqualByComparingTo("120.00");
        assertThat(saida.get("compra_id")).isNotNull();
    }

    @Test
    void segundoPagamentoDeveRetornar409() throws Exception {
        long conta = compraRecebida("1:15.00");
        pagar(conta, "PIX").andExpect(status().isOk());

        pagar(conta, "PIX").andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("Esta conta já foi paga."));
        assertThat(lancamentos("conta_pagar_id = ?", conta)).isEqualTo(1);
    }
}
