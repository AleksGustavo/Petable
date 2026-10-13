package br.com.tecnodreamer.petconnect.compra.web;

import com.jayway.jsonpath.JsonPath;
import br.com.tecnodreamer.petconnect.produto.web.EstoqueIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CompraControllerIT extends EstoqueIntegrationTest {

    private static final String URL = "/api/v1/compras";

    private long fornecedor;
    private long racao;
    private long brinquedo;

    /** Cenário: ração com 10 unidades a R$ 20,00; brinquedo zerado. */
    private void cenario() throws Exception {
        fornecedor = criarFornecedor(unico("Fornecedor Compra"));
        racao = criarProduto(unico("Ração Compra"), null, "20.00", 5);
        brinquedo = criarProduto(unico("Brinquedo Compra"), null, "0", 2);
        ajustar(racao, 10, "Saldo inicial do cenário").andExpect(status().isCreated());
    }

    private long pedidoPadrao() throws Exception {
        return criarCompra(fornecedor, String.valueOf(racao), "5:26.00", String.valueOf(brinquedo), "10:30.50");
    }

    private org.springframework.test.web.servlet.ResultActions cancelar(long id, String motivo) throws Exception {
        return mvc.perform(post(URL + "/" + id + "/cancelar").with(comPapel("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\": \"" + motivo + "\"}"));
    }

    @Test
    void adminDeveCriarCompra() throws Exception {
        cenario();
        long id = pedidoPadrao();

        mvc.perform(get(URL + "/" + id).with(comPapel("VENDAS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PEDIDO"))
                .andExpect(jsonPath("$.dataEntrega").doesNotExist())
                .andExpect(jsonPath("$.itens", hasSize(2)));
        assertThat(acoesAuditadas("compra", id)).contains("COMPRA_CRIADA");
    }

    @Test
    void vendasNaoDeveCriarCompra() throws Exception {
        cenario();
        mvc.perform(post(URL).with(comPapel("VENDAS")).contentType(MediaType.APPLICATION_JSON)
                .content(compraJson(fornecedor, HOJE, String.valueOf(racao), "1:1.00"))).andExpect(status().isForbidden());
    }

    @Test
    void servicoNaoDeveListarCompras() throws Exception {
        mvc.perform(get(URL).with(comPapel("SERVICO"))).andExpect(status().isForbidden());
        mvc.perform(get(URL).with(comPapel("VENDAS"))).andExpect(status().isOk());
    }

    @Test
    void valorTotalDeveSerCalculadoNoBackend() throws Exception {
        cenario();
        String json = compraJson(fornecedor, HOJE, String.valueOf(racao), "5:26.00", String.valueOf(brinquedo), "3:30.33")
                .replace("\"itens\"", "\"valorTotal\": 1, \"status\": \"RECEBIDO\", \"itens\"");

        mvc.perform(post(URL).with(comPapel("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valorTotal").value(220.99))
                .andExpect(jsonPath("$.status").value("PEDIDO"))
                .andExpect(jsonPath("$.itens[1].subtotal").value(90.99));
    }

    @Test
    void criarCompraNaoDeveAlterarEstoque() throws Exception {
        cenario();
        pedidoPadrao();

        assertThat(saldo(racao)).isEqualTo(10);
        assertThat(saldo(brinquedo)).isZero();
    }

    @Test
    void adminDeveReceberCompra() throws Exception {
        cenario();
        long id = pedidoPadrao();

        receber(id, HOJE).andExpect(status().isOk()).andExpect(jsonPath("$.dataEntrega").value(HOJE.toString()));
        assertThat(acoesAuditadas("compra", id)).contains("COMPRA_RECEBIDA");
    }

    @Test
    void vendasNaoDeveReceberCompra() throws Exception {
        cenario();
        long id = pedidoPadrao();

        mvc.perform(post(URL + "/" + id + "/receber").with(comPapel("VENDAS")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"dataEntrega\": \"" + HOJE + "\", \"dataVencimento\": \"" + HOJE + "\"}")).andExpect(status().isForbidden());
    }

    @Test
    void recebimentoDeveAtualizarSaldo() throws Exception {
        cenario();
        receber(pedidoPadrao(), HOJE).andExpect(status().isOk());

        assertThat(saldo(racao)).isEqualTo(15);
        assertThat(saldo(brinquedo)).isEqualTo(10);
    }

    @Test
    void recebimentoDeveAtualizarCustoMedio() throws Exception {
        cenario();
        receber(pedidoPadrao(), HOJE).andExpect(status().isOk());

        // (10 × 20,00 + 5 × 26,00) / 15 = 22,00; brinquedo sem saldo anterior → 30,50.
        assertThat(custo(racao)).isEqualTo("22.00");
        assertThat(custo(brinquedo)).isEqualTo("30.50");
    }

    @Test
    void recebimentoDeveCriarMovimentacaoDeEstoque() throws Exception {
        cenario();
        long id = pedidoPadrao();
        receber(id, HOJE).andExpect(status().isOk());

        var linhas = jdbc.queryForList("select produto_id, quantidade, saldo_anterior, saldo_resultante from movimentacao_estoque "
                + "where compra_id = ? and tipo = 'ENTRADA_COMPRA' order by produto_id", id);
        assertThat(linhas).hasSize(2);
        assertThat(linhas.getFirst()).containsEntry("quantidade", 5).containsEntry("saldo_anterior", 10).containsEntry("saldo_resultante", 15);
        mvc.perform(get("/api/v1/produtos/" + racao + "/movimentacoes").param("tipo", "ENTRADA_COMPRA").with(comPapel("VENDAS")))
                .andExpect(jsonPath("$.content[0].compraId").value(id));
    }

    @Test
    void recebimentoDeveDefinirStatusRecebido() throws Exception {
        cenario();
        long id = pedidoPadrao();
        receber(id, HOJE).andExpect(jsonPath("$.status").value("RECEBIDO"));

        assertThat(jdbc.queryForObject("select status from compra where id = ?", String.class, id)).isEqualTo("RECEBIDO");
    }

    @Test
    void recebimentoDuplicadoDeveRetornar409() throws Exception {
        cenario();
        long id = pedidoPadrao();
        receber(id, HOJE).andExpect(status().isOk());

        receber(id, HOJE).andExpect(status().isConflict());
        assertThat(saldo(racao)).isEqualTo(15);
    }

    @Test
    void adminDeveCancelarPedido() throws Exception {
        cenario();
        long id = pedidoPadrao();

        cancelar(id, "Fornecedor sem os itens")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"))
                .andExpect(jsonPath("$.observacoes").value("IT\nCancelamento: Fornecedor sem os itens"));
        cancelar(id, "curt").andExpect(status().isBadRequest());
        assertThat(acoesAuditadas("compra", id)).contains("COMPRA_CANCELADA");
    }

    @Test
    void cancelamentoNaoDeveAlterarEstoque() throws Exception {
        cenario();
        cancelar(pedidoPadrao(), "Pedido desistido").andExpect(status().isOk());

        assertThat(saldo(racao)).isEqualTo(10);
        assertThat(custo(racao)).isEqualTo("20.00");
    }

    @Test
    void compraCanceladaNaoPodeSerRecebida() throws Exception {
        cenario();
        long id = pedidoPadrao();
        cancelar(id, "Pedido desistido").andExpect(status().isOk());

        receber(id, HOJE).andExpect(status().isConflict());
        String body = mvc.perform(get(URL).param("status", "CANCELADO").param("fornecedorId", String.valueOf(fornecedor))
                .with(comPapel("VENDAS"))).andReturn().getResponse().getContentAsString();
        assertThat(((Number) JsonPath.read(body, "$.totalElements")).intValue()).isEqualTo(1);
    }

    @Test
    void validacoesDaCompra() throws Exception {
        cenario();
        mvc.perform(post(URL).with(comPapel("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(compraJson(fornecedor, HOJE, String.valueOf(racao), "1:1.00", String.valueOf(racao), "2:1.00")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.itens").exists());
        mvc.perform(post(URL).with(comPapel("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(compraJson(fornecedor, HOJE.plusDays(1), String.valueOf(racao), "1:1.00")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.dataCompra").exists());
        mvc.perform(post(URL).with(comPapel("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(compraJson(fornecedor, HOJE, String.valueOf(racao), "0:0")))
                .andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("dataInicio", HOJE.toString()).param("dataFim", HOJE.minusDays(1).toString())
                .with(comPapel("VENDAS"))).andExpect(status().isBadRequest());
    }
}
