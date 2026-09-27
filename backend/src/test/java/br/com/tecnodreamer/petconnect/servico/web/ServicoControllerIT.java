package br.com.tecnodreamer.petconnect.servico.web;

import com.jayway.jsonpath.JsonPath;
import br.com.tecnodreamer.petconnect.pet.web.PetIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ServicoControllerIT extends PetIntegrationTest {

    private static final String URL = "/api/v1/servicos";

    private static String json(String nome, String precos) {
        return """
                {"nome": "%s", "descricao": "IT", "preco": 50.0, "duracaoMinutos": 45, "precosPorPorte": %s}"""
                .formatted(nome, precos);
    }

    private long criar(String nome) throws Exception {
        String body = mvc.perform(post(URL).with(comPapel("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(json(nome, "[{\"porte\": \"PEQUENO\", \"preco\": 45.0}]")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private java.util.List<String> auditoriaServico(long id) {
        return auditoria.findByEntidadeAndEntidadeId("servico", String.valueOf(id), Pageable.unpaged())
                .map(e -> e.acao()).toList();
    }

    @Test
    void autenticadoDeveListarServicos() throws Exception {
        mvc.perform(get(URL).with(comPapel("SERVICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nome", hasItem("Banho")));
        mvc.perform(get(URL).param("ativo", "true").with(comPapel("SERVICO")))
                .andExpect(jsonPath("$[?(@.nome == 'Banho')].precosPorPorte[*]", hasSize(3)));
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void adminDeveCriarServico() throws Exception {
        String nome = unico("Tosa Higiênica");
        long id = criar(nome);

        mvc.perform(get(URL + "/" + id).with(comPapel("VENDAS")))
                .andExpect(jsonPath("$.nome").value(nome))
                .andExpect(jsonPath("$.precosPorPorte[0].porte").value("PEQUENO"));
        mvc.perform(post(URL).with(comPapel("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(json(nome, "[]")))
                .andExpect(status().isConflict());
        mvc.perform(post(URL).with(comPapel("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(json("X", "[]").replace("45", "2")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nome").exists())
                .andExpect(jsonPath("$.errors.duracaoMinutos").exists());
        assertThat(auditoriaServico(id)).contains("SERVICO_CRIADO");
    }

    @Test
    void vendasNaoDeveCriarServico() throws Exception {
        mvc.perform(post(URL).with(comPapel("VENDAS")).contentType(MediaType.APPLICATION_JSON).content(json(unico("Z"), "[]")))
                .andExpect(status().isForbidden());
        mvc.perform(post(URL).with(comPapel("SERVICO")).contentType(MediaType.APPLICATION_JSON).content(json(unico("Z"), "[]")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminDeveAtualizarPrecosPorPorte() throws Exception {
        String nome = unico("Hidratação Plus");
        long id = criar(nome);

        mvc.perform(put(URL + "/" + id).with(comPapel("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(json(nome, "[{\"porte\": \"MEDIO\", \"preco\": 55.0}, {\"porte\": \"GRANDE\", \"preco\": 70.0}]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precosPorPorte", hasSize(2)))
                .andExpect(jsonPath("$.precosPorPorte[0].porte").value("MEDIO"));

        assertThat(jdbc.queryForList("select porte from preco_servico_porte where servico_id = ? order by porte",
                String.class, id)).containsExactly("GRANDE", "MEDIO");
        assertThat(auditoriaServico(id)).contains("SERVICO_ATUALIZADO");
    }

    @Test
    void adminDeveDesativarServico() throws Exception {
        long id = criar(unico("Temporário"));

        mvc.perform(delete(URL + "/" + id).with(comPapel("VENDAS"))).andExpect(status().isForbidden());
        mvc.perform(delete(URL + "/" + id).with(comPapel("ADMIN"))).andExpect(status().isNoContent());
        mvc.perform(get(URL + "/" + id).with(comPapel("ADMIN"))).andExpect(jsonPath("$.ativo").value(false));
        assertThat(auditoriaServico(id)).contains("SERVICO_DESATIVADO");
    }

    @Test
    void servicoDesativadoNaoDeveAparecerNoSelectAtivo() throws Exception {
        String nome = unico("Descontinuado");
        long id = criar(nome);
        mvc.perform(delete(URL + "/" + id).with(comPapel("ADMIN"))).andExpect(status().isNoContent());

        mvc.perform(get(URL).param("ativo", "true").with(comPapel("VENDAS")))
                .andExpect(jsonPath("$[*].nome", not(hasItem(nome))));

        long petId = criarPet(criarCliente(unico("Tutor Serviço Inativo")), "Duke", "Cão");
        mvc.perform(post("/api/v1/agendamentos").with(comPapel("VENDAS")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"petId": %d, "servicoId": %d, "dataHora": "%s"}"""
                                .formatted(petId, id, LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(3).atTime(9, 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.servicoId").exists());
    }
}
