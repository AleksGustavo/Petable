package br.com.tecnodreamer.petconnect.financeiro.web;

import com.jayway.jsonpath.JsonPath;
import br.com.tecnodreamer.petconnect.venda.web.VendaIntegrationTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Apoio dos ITs do financeiro: operadores novos (com ou sem caixa), caixa e lançamentos. */
public abstract class FinanceiroIntegrationTest extends VendaIntegrationTest {

    protected static final String CAIXAS = "/api/v1/caixas";
    protected static final String FIN = "/api/v1/financeiro";

    /** Usuário novo do papel; {@code abrir}: já com caixa aberto (fundo 0). */
    protected String operador(String papel, boolean abrir) throws Exception {
        String username = nomeUsuario();
        criarUsuario(username, papel);
        String token = login(username, SENHA);
        if (abrir) {
            abrirCaixa(token);
        }
        return token;
    }

    protected long usuarioId(String token) throws Exception {
        String body = mvc.perform(get("/api/v1/me").with(bearer(token))).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    protected ResultActions abrir(String token, String fundo) throws Exception {
        return mvc.perform(post(CAIXAS + "/abrir").with(bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fundoAbertura\": " + fundo + "}"));
    }

    protected ResultActions movimento(String token, String tipo, String valor, String motivo) throws Exception {
        return mvc.perform(post(CAIXAS + "/atual/" + tipo).with(bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"valor\": " + valor + ", \"motivo\": \"" + motivo + "\"}"));
    }

    protected static String contadoJson(String dinheiro, String motivo) {
        return """
                {"contado": {"DINHEIRO": %s, "CARTAO_CREDITO": 0, "CARTAO_DEBITO": 0, "PIX": 0, "TRANSFERENCIA": 0},
                 "motivoDiferenca": %s}""".formatted(dinheiro, motivo == null ? "null" : "\"" + motivo + "\"");
    }

    protected ResultActions fechar(String token, String json) throws Exception {
        return mvc.perform(post(CAIXAS + "/atual/fechar").with(bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected String esperadoDinheiro(String token) throws Exception {
        String body = mvc.perform(get(CAIXAS + "/atual/previa-fechamento").with(bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new java.math.BigDecimal(JsonPath.read(body, "$.esperado.DINHEIRO").toString()).setScale(2).toPlainString();
    }

    protected ResultActions lancarManual(String token, String chave, String tipo, String categoria, String valor) throws Exception {
        return mvc.perform(post(FIN + "/lancamentos").with(bearer(token)).header("Idempotency-Key", chave)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"tipo": "%s", "categoria": "%s", "descricao": "Lançamento IT", "valor": %s, "formaPagamento": "DINHEIRO"}"""
                        .formatted(tipo, categoria, valor)));
    }

    protected static String novaChave() {
        return UUID.randomUUID().toString();
    }

    protected int lancamentos(String where, Object... args) {
        return jdbc.queryForObject("select count(*) from movimentacao_financeira where " + where, Integer.class, args);
    }

    /** Compra de um item recebida pelo ADMIN; devolve o id da conta a pagar criada. */
    protected long compraRecebida(String quantidadeECusto) throws Exception {
        long fornecedor = criarFornecedor(unico("Fornecedor Financeiro"));
        long produtoId = produto("10.00", 0);
        long compra = criarCompra(fornecedor, String.valueOf(produtoId), quantidadeECusto);
        String body = receber(compra, HOJE).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.contaPagar.id")).longValue();
    }
}
