package br.com.tecnodreamer.petconnect.pet.web;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PetControllerIT extends PetIntegrationTest {

    private static final String URL = "/api/v1/pets";

    @Test
    void crudCompletoComExclusaoLogicaEAuditoria() throws Exception {
        long clienteId = criarCliente(unico("Tutora Ana"));
        long cao = especieId("Cão");
        long labrador = racaId("Cão", "Labrador");

        String body = mvc.perform(post(URL).with(comPapel("VENDAS")).contentType(MediaType.APPLICATION_JSON)
                        .content(petJson(clienteId, "Thor", cao, labrador)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.tutor.id").value(clienteId))
                .andExpect(jsonPath("$.especie.nome").value("Cão"))
                .andExpect(jsonPath("$.raca.nome").value("Labrador"))
                .andExpect(jsonPath("$.idade.anos").isNumber())
                .andExpect(jsonPath("$.porte").value("GRANDE"))
                .andExpect(jsonPath("$.fotoUrl").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();

        mvc.perform(put(URL + "/" + id).with(comPapel("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(petJson(clienteId, "Thor Jr", cao, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Thor Jr"))
                .andExpect(jsonPath("$.raca").doesNotExist());

        mvc.perform(get(URL + "/" + id).with(comPapel("SERVICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Thor Jr"));

        mvc.perform(delete(URL + "/" + id).with(comPapel("VENDAS"))).andExpect(status().isNoContent());
        mvc.perform(get(URL + "/" + id).with(comPapel("VENDAS"))).andExpect(status().isNotFound());
        mvc.perform(get(URL).param("clienteId", String.valueOf(clienteId)).with(comPapel("VENDAS")))
                .andExpect(jsonPath("$.totalElements").value(0));

        assertThat(jdbc.queryForObject("select ativo from pet where id = ?", Boolean.class, id)).isFalse();
        assertThat(acoesAuditadas(id)).containsExactlyInAnyOrder("PET_CRIADO", "PET_ATUALIZADO", "PET_DESATIVADO");
    }

    @Test
    void buscaPorNomeDoPetOuDoTutor() throws Exception {
        String tutor = unico("Zuleica Buscavel");
        long clienteId = criarCliente(tutor);
        String nomePet = unico("Pipoca");
        criarPet(clienteId, nomePet, "Gato");

        mvc.perform(get(URL).param("q", tutor.toUpperCase()).with(comPapel("SERVICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].nome").value(nomePet))
                .andExpect(jsonPath("$.content[0].tutor.nome").value(tutor));

        mvc.perform(get(URL).param("q", nomePet.toLowerCase()).with(comPapel("SERVICO")))
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    void paginaEValidaParametros() throws Exception {
        long clienteId = criarCliente(unico("Tutor Paginado"));
        for (String nome : new String[] {"Alfa", "Beta", "Gama"}) {
            criarPet(clienteId, nome, "Cão");
        }

        mvc.perform(get(URL).param("clienteId", String.valueOf(clienteId)).param("size", "2").with(comPapel("VENDAS")))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].nome").value("Alfa"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mvc.perform(get(URL).param("size", "0").with(comPapel("VENDAS"))).andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("size", "101").with(comPapel("VENDAS"))).andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("page", "-1").with(comPapel("VENDAS"))).andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("q", "x".repeat(101)).with(comPapel("VENDAS"))).andExpect(status().isBadRequest());
    }

    @Test
    void racaDeOutraEspecieResponde409() throws Exception {
        long clienteId = criarCliente(unico("Tutor Conflito"));

        mvc.perform(post(URL).with(comPapel("VENDAS")).contentType(MediaType.APPLICATION_JSON)
                        .content(petJson(clienteId, "Mingau", especieId("Gato"), racaId("Cão", "Labrador"))))
                .andExpect(status().isConflict());
    }

    @Test
    void tutorInexistenteOuDadosInvalidosResponde400NoCampo() throws Exception {
        mvc.perform(post(URL).with(comPapel("VENDAS")).contentType(MediaType.APPLICATION_JSON)
                        .content(petJson(999_999, "Sem Tutor", especieId("Cão"), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.clienteId").exists());

        long clienteId = criarCliente(unico("Tutor Validacao"));
        mvc.perform(post(URL).with(comPapel("VENDAS")).contentType(MediaType.APPLICATION_JSON)
                        .content(petJson(clienteId, "", especieId("Cão"), null).replace("32.5", "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nome").exists())
                .andExpect(jsonPath("$.errors.peso").exists());
    }

    @Test
    void servicoSoLeESemTokenResponde401() throws Exception {
        long clienteId = criarCliente(unico("Tutor Permissao"));
        long id = criarPet(clienteId, "Bidu", "Cão");
        String json = petJson(clienteId, "Bidu", especieId("Cão"), null);

        mvc.perform(get(URL + "/" + id).with(comPapel("SERVICO"))).andExpect(status().isOk());
        mvc.perform(post(URL).with(comPapel("SERVICO")).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isForbidden());
        mvc.perform(put(URL + "/" + id).with(comPapel("SERVICO")).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isForbidden());
        mvc.perform(delete(URL + "/" + id).with(comPapel("SERVICO"))).andExpect(status().isForbidden());
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }
}
