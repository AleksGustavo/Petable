package br.com.tecnodreamer.petconnect.venda.web;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VendaControllerIT extends VendaIntegrationTest {

    @Test
    void vendasDeveRegistrarVenda() throws Exception {
        long p = produto("169.90", 10);
        String token = vendas();

        vender(token, UUID.randomUUID().toString(), vendaJson("PIX", "5.00", "null", p, 2))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("CONCLUIDA"))
                .andExpect(jsonPath("$.valorTotal").value(334.8))
                .andExpect(jsonPath("$.operador.nome").isString())
                .andExpect(jsonPath("$.itens[0].precoUnitario").value(169.9));
    }

    @Test
    void adminDeveRegistrarVenda() throws Exception {
        long p = produto("30.00", 5);
        long id = venderOk(admin(), vendaJson("DINHEIRO", "0", "50.00", p, 1));

        mvc.perform(get(URL + "/" + id).with(comPapel("VENDAS")))
                .andExpect(jsonPath("$.troco").value(20.0))
                .andExpect(jsonPath("$.operador.nome").value(ADMIN_USER));
        assertThat(acoesAuditadas("venda", id)).contains("VENDA_REALIZADA");
    }

    @Test
    void servicoNaoDeveVender() throws Exception {
        mvc.perform(post(URL).with(comPapel("SERVICO")).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(post(URL + "/previa").with(comPapel("SERVICO")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void servicoNaoDeveListarVendas() throws Exception {
        mvc.perform(get(URL).with(comPapel("SERVICO"))).andExpect(status().isForbidden());
        mvc.perform(get(URL + "/resumo-dia").with(comPapel("SERVICO"))).andExpect(status().isForbidden());
        mvc.perform(get(URL).with(comPapel("VENDAS"))).andExpect(status().isOk());
    }

    @Test
    void semIdempotencyKeyDeveRetornar400() throws Exception {
        long p = produto("10.00", 5);
        String token = vendas();

        vender(token, null, vendaJson("PIX", "0", "null", p, 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.idempotencyKey").exists());
        vender(token, "abc", vendaJson("PIX", "0", "null", p, 1)).andExpect(status().isBadRequest());
        assertThat(saldo(p)).isEqualTo(5);
    }

    @Test
    void mesmaChaveDeveRetornar200SemNovaBaixa() throws Exception {
        long p = produto("10.00", 5);
        String token = vendas();
        String chave = UUID.randomUUID().toString();
        String json = vendaJson("PIX", "0", "null", p, 2);

        String primeira = vender(token, chave, json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        vender(token, chave, json)
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(((Number) JsonPath.read(primeira, "$.id")).intValue()));

        assertThat(saldo(p)).isEqualTo(3);
        assertThat(movimentacoes(p, "SAIDA_VENDA")).isEqualTo(1);
        // Mesma chave de outro operador: 409.
        vender(admin(), chave, json).andExpect(status().isConflict());
    }

    @Test
    void vendaDeveBaixarEstoqueEGerarSaidaVenda() throws Exception {
        long p = produto("10.00", 5);
        long id = venderOk(vendas(), vendaJson("PIX", "0", "null", p, 3));

        assertThat(saldo(p)).isEqualTo(2);
        var linha = jdbc.queryForMap("select quantidade, saldo_anterior, saldo_resultante, venda_id from movimentacao_estoque "
                + "where produto_id = ? and tipo = 'SAIDA_VENDA'", p);
        assertThat(linha).containsEntry("quantidade", -3).containsEntry("saldo_anterior", 5).containsEntry("saldo_resultante", 2);
        assertThat(((Number) linha.get("venda_id")).longValue()).isEqualTo(id);
    }

    @Test
    void estoqueInsuficienteDeveRetornar409SemGravarNada() throws Exception {
        long a = produto("10.00", 5);
        long b = produto("20.00", 1);
        int vendasAntes = jdbc.queryForObject("select count(*) from venda", Integer.class);

        vender(vendas(), UUID.randomUUID().toString(), vendaJson("PIX", "0", "null", a, 2, b, 2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.produtoId").value(b))
                .andExpect(jsonPath("$.disponivel").value(1))
                .andExpect(jsonPath("$.solicitado").value(2));

        assertThat(saldo(a)).isEqualTo(5);
        assertThat(movimentacoes(a, "SAIDA_VENDA")).isZero();
        assertThat(jdbc.queryForObject("select count(*) from venda", Integer.class)).isEqualTo(vendasAntes);
    }

    @Test
    void totalDeveSerCalculadoNoBackend() throws Exception {
        long p = produto("169.90", 10);
        long q = produto("30.00", 10);
        String json = vendaJson("PIX", "5.00", "null", p, 2, q, 1)
                .replace("\"itens\"", "\"valorTotal\": 1, \"status\": \"CANCELADA\", \"dataVenda\": \"2020-01-01T00:00:00\", \"itens\"")
                .replace("\"quantidade\": 2}", "\"quantidade\": 2, \"precoUnitario\": 0.01}");

        vender(vendas(), UUID.randomUUID().toString(), json)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(369.8))
                .andExpect(jsonPath("$.valorTotal").value(364.8))
                .andExpect(jsonPath("$.status").value("CONCLUIDA"))
                .andExpect(jsonPath("$.itens[0].precoUnitario").value(169.9));
    }

    @Test
    void descontoAcimaDoTetoParaVendasDeveRetornar400() throws Exception {
        long p = produto("100.00", 10);

        vender(vendas(), UUID.randomUUID().toString(), vendaJson("PIX", "10.01", "null", p, 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.desconto").value("Desconto acima do permitido para o seu perfil (máximo 10%)."));
        vender(admin(), UUID.randomUUID().toString(), vendaJson("PIX", "50.00", "null", p, 1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valorTotal").value(50.0));
        vender(admin(), UUID.randomUUID().toString(), vendaJson("DINHEIRO", "0", "99.99", p, 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.valorRecebido").exists());
    }

    @Test
    void deveListarVendasPorPeriodoEStatus() throws Exception {
        long cliente = criarCliente(unico("Cliente PDV"));
        long p = produto("10.00", 10);
        String token = admin();
        String json = vendaJson("PIX", "0", "null", p, 1).replace("{\"itens\"", "{\"clienteId\": " + cliente + ", \"itens\"");
        venderOk(token, json);
        long cancelada = venderOk(token, json);
        cancelar(token, cancelada, "Cliente desistiu").andExpect(status().isOk());

        mvc.perform(get(URL).param("clienteId", String.valueOf(cliente)).param("dataInicio", HOJE.toString())
                        .param("dataFim", HOJE.toString()).with(comPapel("VENDAS")))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].id").value(cancelada))
                .andExpect(jsonPath("$.content[0].itens").doesNotExist());
        mvc.perform(get(URL).param("clienteId", String.valueOf(cliente)).param("status", "CANCELADA").with(comPapel("VENDAS")))
                .andExpect(jsonPath("$.content", hasSize(1)));
        mvc.perform(get(URL).param("clienteId", String.valueOf(cliente)).param("dataInicio", HOJE.plusDays(1).toString())
                .with(comPapel("VENDAS"))).andExpect(jsonPath("$.content", hasSize(0)));
        mvc.perform(get(URL).param("dataInicio", HOJE.toString()).param("dataFim", HOJE.minusDays(1).toString())
                .with(comPapel("VENDAS"))).andExpect(status().isBadRequest());
    }

    @Test
    void resumoDiaDeveContarSoConcluidas() throws Exception {
        String antes = mvc.perform(get(URL + "/resumo-dia").with(comPapel("VENDAS"))).andReturn().getResponse().getContentAsString();
        long p = produto("12.34", 10);
        String token = admin();
        venderOk(token, vendaJson("PIX", "0", "null", p, 1));
        long cancelada = venderOk(token, vendaJson("PIX", "0", "null", p, 2));
        cancelar(token, cancelada, "Cancelada no teste").andExpect(status().isOk());

        String depois = mvc.perform(get(URL + "/resumo-dia").with(comPapel("VENDAS")))
                .andExpect(jsonPath("$.data").value(HOJE.toString())).andReturn().getResponse().getContentAsString();
        assertThat(((Number) JsonPath.read(depois, "$.quantidade")).intValue() - ((Number) JsonPath.read(antes, "$.quantidade")).intValue())
                .isEqualTo(1);
        BigDecimal delta = new BigDecimal(JsonPath.read(depois, "$.total").toString())
                .subtract(new BigDecimal(JsonPath.read(antes, "$.total").toString()));
        assertThat(delta).isEqualByComparingTo("12.34");
    }

    @Test
    void adminDeveCancelarVendaComEstorno() throws Exception {
        long p = produto("10.00", 5);
        String token = admin();
        long id = venderOk(token, vendaJson("PIX", "0", "null", p, 3));

        cancelar(token, id, "Cliente desistiu na hora")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"))
                .andExpect(jsonPath("$.motivoCancelamento").value("Cliente desistiu na hora"))
                .andExpect(jsonPath("$.canceladaPor.nome").value(ADMIN_USER));

        assertThat(saldo(p)).isEqualTo(5);
        var estorno = jdbc.queryForMap("select quantidade, venda_id from movimentacao_estoque where produto_id = ? and tipo = 'ESTORNO_VENDA'", p);
        assertThat(estorno).containsEntry("quantidade", 3);
        assertThat(((Number) estorno.get("venda_id")).longValue()).isEqualTo(id);
        assertThat(acoesAuditadas("venda", id)).contains("VENDA_CANCELADA");
    }

    @Test
    void vendasNaoDeveCancelarVenda() throws Exception {
        long p = produto("10.00", 5);
        String token = vendas();
        long id = venderOk(token, vendaJson("PIX", "0", "null", p, 1));

        cancelar(token, id, "Tentativa de cancelar").andExpect(status().isForbidden());
        assertThat(saldo(p)).isEqualTo(4);
    }

    @Test
    void cancelamentoDuplicadoDeveRetornar409() throws Exception {
        long p = produto("10.00", 5);
        String token = admin();
        long id = venderOk(token, vendaJson("PIX", "0", "null", p, 1));
        cancelar(token, id, "Primeiro cancelamento").andExpect(status().isOk());

        cancelar(token, id, "Segundo cancelamento")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A venda já está cancelada."));
        assertThat(saldo(p)).isEqualTo(5);
        assertThat(movimentacoes(p, "ESTORNO_VENDA")).isEqualTo(1);
        cancelar(token, id, "curt").andExpect(status().isBadRequest());
    }

    @Test
    void previaDeveMostrarDisponibilidadeSemAlterarEstoque() throws Exception {
        long p = produto("10.00", 2);

        mvc.perform(post(URL + "/previa").with(bearer(vendas())).contentType(MediaType.APPLICATION_JSON)
                        .content(vendaJson("DINHEIRO", "1.00", "50.00", p, 3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal").value(30.0))
                .andExpect(jsonPath("$.total").value(29.0))
                .andExpect(jsonPath("$.troco").value(21.0))
                .andExpect(jsonPath("$.itens[0].disponivel").value(2))
                .andExpect(jsonPath("$.itens[0].suficiente").value(false))
                .andExpect(jsonPath("$.podeFinalizar").value(false));
        assertThat(saldo(p)).isEqualTo(2);
        assertThat(movimentacoes(p, "SAIDA_VENDA")).isZero();
    }
}
