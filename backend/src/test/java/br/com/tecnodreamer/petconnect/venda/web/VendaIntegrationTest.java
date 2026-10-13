package br.com.tecnodreamer.petconnect.venda.web;

import com.jayway.jsonpath.JsonPath;
import br.com.tecnodreamer.petconnect.produto.web.EstoqueIntegrationTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Apoio dos ITs de venda: POST de venda exige token REAL (o operador vem do uid do JWT). */
public abstract class VendaIntegrationTest extends EstoqueIntegrationTest {

    protected static final String URL = "/api/v1/vendas";

    protected String vendas() throws Exception {
        String username = nomeUsuario();
        criarUsuario(username, "VENDAS");
        String token = login(username, SENHA);
        abrirCaixa(token);
        return token;
    }

    protected long adminId() {
        return jdbc.queryForObject("select id from usuario where username = ?", Long.class, ADMIN_USER);
    }

    /** Produto ativo com preço de venda e saldo definidos (saldo entra por ajuste do ADMIN). */
    protected long produto(String preco, int saldo) throws Exception {
        String body = mvc.perform(post("/api/v1/produtos").with(comPapel("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(produtoJson(unico("Produto PDV"), categoriaId("Ração"), codigoBarras(), "10.00", preco, 1)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        if (saldo > 0) {
            ajustar(id, saldo, "Saldo inicial do teste").andExpect(status().isCreated());
        }
        return id;
    }

    /** itens: pares (produtoId, quantidade). */
    protected static String vendaJson(String forma, String desconto, String recebido, long... itens) {
        StringBuilder json = new StringBuilder();
        for (int i = 0; i < itens.length; i += 2) {
            if (json.length() > 0) {
                json.append(',');
            }
            json.append("{\"produtoId\": %d, \"quantidade\": %d}".formatted(itens[i], itens[i + 1]));
        }
        return """
                {"itens": [%s], "desconto": %s, "formaPagamento": "%s", "valorRecebido": %s}"""
                .formatted(json, desconto, forma, recebido);
    }

    protected ResultActions vender(String token, String chave, String json) throws Exception {
        var req = post(URL).with(bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json);
        if (chave != null) {
            req.header("Idempotency-Key", chave);
        }
        return mvc.perform(req);
    }

    protected long venderOk(String token, String json) throws Exception {
        String body = vender(token, UUID.randomUUID().toString(), json).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    protected ResultActions cancelar(String token, long vendaId, String motivo) throws Exception {
        return mvc.perform(post(URL + "/" + vendaId + "/cancelar").with(bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\": \"" + motivo + "\"}"));
    }

    protected int movimentacoes(long produtoId, String tipo) {
        return jdbc.queryForObject("select count(*) from movimentacao_estoque where produto_id = ? and tipo = ?",
                Integer.class, produtoId, tipo);
    }
}
