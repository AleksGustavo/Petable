package br.com.tecnodreamer.petconnect.fornecedor.web;

import br.com.tecnodreamer.petconnect.produto.web.EstoqueIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FornecedorControllerIT extends EstoqueIntegrationTest {

    private static final String URL = "/api/v1/fornecedores";

    private java.util.List<String> auditoriaFornecedor(long id) {
        return acoesAuditadas("fornecedor", id);
    }

    @Test
    void adminDeveCriarFornecedor() throws Exception {
        String cnpj = cnpjAleatorio();
        String nome = unico("Pet Distribuidora");

        mvc.perform(post(URL).with(comPapel("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(fornecedorJson(nome, cnpj, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cnpj").value(cnpj))
                .andExpect(jsonPath("$.endereco.cidade").value("Leme"))
                .andExpect(jsonPath("$.ativo").value(true));
        long id = jdbc.queryForObject("select id from fornecedor where cnpj = ?", Long.class, cnpj);
        assertThat(auditoriaFornecedor(id)).contains("FORNECEDOR_CRIADO");
        mvc.perform(get(URL).param("q", cnpj.substring(0, 8)).with(comPapel("VENDAS"))).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(URL + "/opcoes").param("q", nome).with(comPapel("VENDAS"))).andExpect(jsonPath("$[0].id").value(id));
    }

    @Test
    void vendasNaoDeveCriarFornecedor() throws Exception {
        mvc.perform(post(URL).with(comPapel("VENDAS")).contentType(MediaType.APPLICATION_JSON)
                .content(fornecedorJson("X", cnpjAleatorio(), null))).andExpect(status().isForbidden());
    }

    @Test
    void servicoNaoDeveListarFornecedores() throws Exception {
        mvc.perform(get(URL).with(comPapel("SERVICO"))).andExpect(status().isForbidden());
        mvc.perform(get(URL + "/opcoes").with(comPapel("SERVICO"))).andExpect(status().isForbidden());
    }

    @Test
    void deveRetornar400ParaCnpjInvalido() throws Exception {
        mvc.perform(post(URL).with(comPapel("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(fornecedorJson(unico("Inválido"), "11222333000180", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.cnpj").exists());
    }

    @Test
    void deveRetornar409ParaCnpjDuplicado() throws Exception {
        String cnpj = cnpjAleatorio();
        mvc.perform(post(URL).with(comPapel("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(fornecedorJson(unico("A"), cnpj, null)))
                .andExpect(status().isCreated());

        mvc.perform(post(URL).with(comPapel("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(fornecedorJson(unico("B"), cnpj, null)))
                .andExpect(status().isConflict());
    }

    @Test
    void deveRetornar409ParaTelefoneDuplicado() throws Exception {
        String telefone = telefoneAleatorio();
        mvc.perform(post(URL).with(comPapel("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content(fornecedorJson(unico("C"), cnpjAleatorio(), telefone))).andExpect(status().isCreated());

        mvc.perform(post(URL).with(comPapel("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(fornecedorJson(unico("D"), cnpjAleatorio(), telefone)))
                .andExpect(status().isConflict());
    }

    @Test
    void adminDeveAtualizarFornecedor() throws Exception {
        long id = criarFornecedor(unico("Atualizar"));
        String cnpj = jdbc.queryForObject("select cnpj from fornecedor where id = ?", String.class, id);

        mvc.perform(put(URL + "/" + id).with(comPapel("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(fornecedorJson("Nome Atualizado", cnpj, null).replace("Leme", "Araras")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeFantasia").value("Nome Atualizado"))
                .andExpect(jsonPath("$.endereco.cidade").value("Araras"));
        mvc.perform(put(URL + "/" + id).with(comPapel("VENDAS")).contentType(MediaType.APPLICATION_JSON)
                .content(fornecedorJson("X", cnpj, null))).andExpect(status().isForbidden());
        assertThat(auditoriaFornecedor(id)).contains("FORNECEDOR_ATUALIZADO");
    }

    @Test
    void deveImpedirDesativacaoComCompraAberta() throws Exception {
        long fornecedorId = criarFornecedor(unico("Com Pedido"));
        long produtoId = criarProduto(unico("Item Pedido"), null, "1.00", 1);
        long compraId = criarCompra(fornecedorId, String.valueOf(produtoId), "1:5.00");

        mvc.perform(patch(URL + "/" + fornecedorId + "/desativar").with(comPapel("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("O fornecedor possui compras em aberto e não pode ser desativado."));

        mvc.perform(post("/api/v1/compras/" + compraId + "/cancelar").with(comPapel("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\": \"Pedido desistido\"}")).andExpect(status().isOk());
        mvc.perform(patch(URL + "/" + fornecedorId + "/desativar").with(comPapel("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(false));
        mvc.perform(patch(URL + "/" + fornecedorId + "/ativar").with(comPapel("ADMIN"))).andExpect(jsonPath("$.ativo").value(true));
        assertThat(auditoriaFornecedor(fornecedorId)).contains("FORNECEDOR_DESATIVADO", "FORNECEDOR_ATIVADO");
    }
}
