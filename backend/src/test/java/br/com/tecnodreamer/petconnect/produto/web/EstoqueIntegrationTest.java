package br.com.tecnodreamer.petconnect.produto.web;

import com.jayway.jsonpath.JsonPath;
import br.com.tecnodreamer.petconnect.pet.web.PetIntegrationTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Apoio dos ITs de produtos, estoque, fornecedores e compras. */
public abstract class EstoqueIntegrationTest extends PetIntegrationTest {

    protected static final LocalDate HOJE = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    private String tokenAdmin;

    /** Token REAL do ADMIN do bootstrap: movimentações precisam do usuário (claim uid). */
    protected String admin() throws Exception {
        if (tokenAdmin == null) {
            tokenAdmin = login(ADMIN_USER, ADMIN_PASSWORD);
            // Etapa 06: vender, cancelar e pagar exigem caixa aberto do ADMIN (compartilhado entre os ITs).
            abrirCaixa(tokenAdmin);
        }
        return tokenAdmin;
    }

    protected long categoriaId(String nome) {
        return jdbc.queryForObject("select id from categoria_produto where nome = ?", Long.class, nome);
    }

    protected static String codigoBarras() {
        return "789" + ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L);
    }

    protected static String produtoJson(String nome, long categoriaId, String codigo, String custo, String venda, int minimo) {
        return """
                {"nome": "%s", "descricao": "IT", "categoriaId": %d, "codigoBarras": %s, "precoCusto": %s,
                 "precoVenda": %s, "estoqueMinimo": %d}"""
                .formatted(nome, categoriaId, codigo == null ? "null" : "\"" + codigo + "\"", custo, venda, minimo);
    }

    protected long criarProduto(String nome, String codigo, String custo, int minimo) throws Exception {
        String body = mvc.perform(post("/api/v1/produtos").with(comPapel("VENDAS")).contentType(MediaType.APPLICATION_JSON)
                        .content(produtoJson(nome, categoriaId("Ração"), codigo, custo, "99.90", minimo)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    protected ResultActions ajustar(long produtoId, int delta, String motivo) throws Exception {
        return mvc.perform(post("/api/v1/produtos/" + produtoId + "/ajustes-estoque").with(bearer(admin()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantidade\": %d, \"motivo\": \"%s\"}".formatted(delta, motivo)));
    }

    protected int saldo(long produtoId) {
        return jdbc.queryForObject("select quantidade_estoque from produto where id = ?", Integer.class, produtoId);
    }

    protected String custo(long produtoId) {
        return jdbc.queryForObject("select preco_custo from produto where id = ?", String.class, produtoId);
    }

    /** CNPJ aleatório com dígitos verificadores válidos. */
    protected static String cnpjAleatorio() {
        int[] d = new int[14];
        for (int i = 0; i < 8; i++) {
            d[i] = ThreadLocalRandom.current().nextInt(10);
        }
        d[8] = 0;
        d[9] = 0;
        d[10] = 0;
        d[11] = 1;
        for (int t = 12; t <= 13; t++) {
            int soma = 0;
            int peso = t - 7;
            for (int i = 0; i < t; i++) {
                soma += d[i] * peso;
                peso = peso == 2 ? 9 : peso - 1;
            }
            int resto = soma % 11;
            d[t] = resto < 2 ? 0 : 11 - resto;
        }
        StringBuilder sb = new StringBuilder();
        for (int x : d) {
            sb.append(x);
        }
        return sb.toString();
    }

    protected static String fornecedorJson(String nome, String cnpj, String telefone) {
        return """
                {"nomeFantasia": "%s", "razaoSocial": "%s Ltda", "cnpj": "%s", "telefone": %s,
                 "email": "contato@fornecedor.com.br", "contato": "Carlos",
                 "endereco": {"cep": "13610000", "logradouro": "Rua Exemplo", "numero": "500",
                              "bairro": "Distrito Industrial", "cidade": "Leme", "uf": "SP"}}"""
                .formatted(nome, nome, cnpj, telefone == null ? "null" : "\"" + telefone + "\"");
    }

    protected long criarFornecedor(String nome) throws Exception {
        String body = mvc.perform(post("/api/v1/fornecedores").with(comPapel("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(fornecedorJson(nome, cnpjAleatorio(), null)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    /** itens: pares (produtoId, "quantidade:custo"). */
    protected static String compraJson(long fornecedorId, LocalDate data, String... itens) {
        StringBuilder json = new StringBuilder();
        for (int i = 0; i < itens.length; i += 2) {
            String[] qc = itens[i + 1].split(":");
            if (json.length() > 0) {
                json.append(',');
            }
            json.append("{\"produtoId\": %s, \"quantidade\": %s, \"precoCustoUnitario\": %s}".formatted(itens[i], qc[0], qc[1]));
        }
        return """
                {"fornecedorId": %d, "dataCompra": "%s", "formaPagamento": "BOLETO", "observacoes": "IT", "itens": [%s]}"""
                .formatted(fornecedorId, data, json);
    }

    protected long criarCompra(long fornecedorId, String... itens) throws Exception {
        String body = mvc.perform(post("/api/v1/compras").with(comPapel("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(compraJson(fornecedorId, HOJE, itens)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    protected ResultActions receber(long compraId, LocalDate data) throws Exception {
        return mvc.perform(post("/api/v1/compras/" + compraId + "/receber").with(bearer(admin()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dataEntrega\": \"" + data + "\", \"dataVencimento\": \"" + data.plusDays(30) + "\"}"));
    }
}
