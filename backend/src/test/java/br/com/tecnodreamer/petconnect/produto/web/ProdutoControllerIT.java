package br.com.tecnodreamer.petconnect.produto.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProdutoControllerIT extends EstoqueIntegrationTest {

    private static final String URL = "/api/v1/produtos";

    @Test
    void autenticadoDeveListarProdutos() throws Exception {
        String nome = unico("Petisco Listagem");
        criarProduto(nome, null, "5.00", 2);

        mvc.perform(get(URL).param("q", nome).with(comPapel("SERVICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nome").value(nome))
                .andExpect(jsonPath("$.content[0].quantidadeEstoque").value(0))
                .andExpect(jsonPath("$.content[0].categoria.nome").value("Ração"));
        mvc.perform(get("/api/v1/categorias-produto").with(comPapel("SERVICO")))
                .andExpect(jsonPath("$[0].nome").value("Acessório"));
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void deveBuscarProdutoPorNome() throws Exception {
        String nome = unico("Coleira Buscável");
        criarProduto(nome, null, "5.00", 2);

        mvc.perform(get(URL).param("q", nome.toLowerCase()).with(comPapel("VENDAS")))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void deveBuscarProdutoPorCodigoDeBarras() throws Exception {
        String codigo = codigoBarras();
        long id = criarProduto(unico("Shampoo"), codigo, "5.00", 2);

        mvc.perform(get(URL).param("q", codigo.substring(0, 10)).with(comPapel("VENDAS")))
                .andExpect(jsonPath("$.content[*].id", hasItem((int) id)));
    }

    @Test
    void deveFiltrarProdutosAbaixoDoMinimo() throws Exception {
        String prefixo = unico("Filtro Mínimo");
        long abaixo = criarProduto(prefixo + " A", null, "5.00", 5);
        long acima = criarProduto(prefixo + " B", null, "5.00", 5);
        ajustar(abaixo, 5, "Saldo igual ao mínimo").andExpect(status().isCreated());
        ajustar(acima, 6, "Saldo acima do mínimo").andExpect(status().isCreated());

        mvc.perform(get(URL).param("q", prefixo).param("abaixoMinimo", "true").with(comPapel("VENDAS")))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(abaixo))
                .andExpect(jsonPath("$.content[0].abaixoDoMinimo").value(true));
        mvc.perform(get(URL + "/resumo-estoque").with(comPapel("SERVICO")))
                .andExpect(jsonPath("$.produtosAbaixoDoMinimo").isNumber());
    }

    @Test
    void adminDeveCriarProduto() throws Exception {
        mvc.perform(post(URL).with(comPapel("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(produtoJson(unico("Ração Admin"), categoriaId("Ração"), codigoBarras(), "120.50", "169.90", 5)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantidadeEstoque").value(0))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.precoCusto").value(120.5));
    }

    @Test
    void vendasDeveCriarProduto() throws Exception {
        long id = criarProduto(unico("Ração Vendas"), null, "10.00", 3);
        assertThat(acoesAuditadas("produto", id)).contains("PRODUTO_CRIADO");
        // Saldo informado no corpo é ignorado: produto nasce com 0.
        mvc.perform(post(URL).with(comPapel("VENDAS")).contentType(MediaType.APPLICATION_JSON)
                        .content(produtoJson(unico("Com saldo"), categoriaId("Ração"), null, "1", "2", 1)
                                .replace("}", ", \"quantidadeEstoque\": 500}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantidadeEstoque").value(0));
    }

    @Test
    void servicoNaoDeveCriarProduto() throws Exception {
        mvc.perform(post(URL).with(comPapel("SERVICO")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deveRetornar409ParaCodigoDuplicado() throws Exception {
        String codigo = codigoBarras();
        criarProduto(unico("Primeiro"), codigo, "1.00", 1);

        mvc.perform(post(URL).with(comPapel("VENDAS")).contentType(MediaType.APPLICATION_JSON)
                        .content(produtoJson(unico("Segundo"), categoriaId("Ração"), codigo, "1", "2", 1)))
                .andExpect(status().isConflict());
        mvc.perform(post(URL).with(comPapel("VENDAS")).contentType(MediaType.APPLICATION_JSON)
                        .content(produtoJson(unico("Inválido"), categoriaId("Ração"), "1234567", "1", "0", -1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.codigoBarras").exists())
                .andExpect(jsonPath("$.errors.precoVenda").exists())
                .andExpect(jsonPath("$.errors.estoqueMinimo").exists());
    }

    @Test
    void vendasDeveDesativarProduto() throws Exception {
        long id = criarProduto(unico("Para Desativar"), null, "1.00", 1);
        ajustar(id, 4, "Saldo inicial de teste").andExpect(status().isCreated());

        mvc.perform(patch(URL + "/" + id + "/desativar").with(comPapel("VENDAS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false))
                .andExpect(jsonPath("$.quantidadeEstoque").value(4));
        assertThat(acoesAuditadas("produto", id)).contains("PRODUTO_DESATIVADO");
    }

    @Test
    void vendasNaoDeveReativarProduto() throws Exception {
        long id = criarProduto(unico("Reativar Vendas"), null, "1.00", 1);
        mvc.perform(patch(URL + "/" + id + "/desativar").with(comPapel("VENDAS"))).andExpect(status().isOk());

        mvc.perform(patch(URL + "/" + id + "/ativar").with(comPapel("VENDAS"))).andExpect(status().isForbidden());
    }

    @Test
    void adminDeveReativarProduto() throws Exception {
        long id = criarProduto(unico("Reativar Admin"), null, "1.00", 1);
        mvc.perform(patch(URL + "/" + id + "/desativar").with(comPapel("VENDAS"))).andExpect(status().isOk());

        mvc.perform(patch(URL + "/" + id + "/ativar").with(comPapel("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(true));
        assertThat(acoesAuditadas("produto", id)).contains("PRODUTO_ATIVADO");
    }

    @Test
    void buscaRapidaDeveRetornarSomenteAtivos() throws Exception {
        String prefixo = unico("Rápida Ativos");
        criarProduto(prefixo + " Ativo", null, "1.00", 1);
        long inativo = criarProduto(prefixo + " Inativo", null, "1.00", 1);
        mvc.perform(patch(URL + "/" + inativo + "/desativar").with(comPapel("VENDAS"))).andExpect(status().isOk());

        mvc.perform(get(URL + "/busca-rapida").param("q", prefixo).with(comPapel("SERVICO")))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nome").value(prefixo + " Ativo"))
                .andExpect(jsonPath("$[0].disponivel").value(false));
        mvc.perform(get(URL + "/busca-rapida").param("q", "x").with(comPapel("SERVICO"))).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void codigoExatoDeveTerPrioridadeNaBuscaRapida() throws Exception {
        String codigo = codigoBarras();
        // Outro produto cujo NOME contém o código aparece depois do dono do código.
        criarProduto("Aaa " + codigo, null, "1.00", 1);
        long dono = criarProduto(unico("Zzz Dono"), codigo, "1.00", 1);

        mvc.perform(get(URL + "/busca-rapida").param("q", codigo).with(comPapel("VENDAS")))
                .andExpect(jsonPath("$[0].id").value(dono))
                .andExpect(jsonPath("$[*].codigoBarras", everyItem(not(hasItem("nada")))));
    }
}
