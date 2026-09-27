package br.com.tecnodreamer.petconnect.pet.web;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatalogoPetControllerIT extends PetIntegrationTest {

    @Test
    void listaEspeciesOrdenadas() throws Exception {
        mvc.perform(get("/api/v1/especies").with(comPapel("SERVICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("Ave"))
                .andExpect(jsonPath("$[*].nome", hasItems("Cão", "Gato")));
    }

    @Test
    void racasFiltradasPelaEspecie() throws Exception {
        mvc.perform(get("/api/v1/especies/" + especieId("Gato") + "/racas").with(comPapel("VENDAS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nome", hasItems("Siamês", "Persa")))
                .andExpect(jsonPath("$[*].nome", not(hasItem("Labrador"))));
    }

    @Test
    void vacinasAtivasFiltradasPelaEspecie() throws Exception {
        long gato = especieId("Gato");
        jdbc.update("insert into vacina (especie_id, nome, ativo) values (?, 'Vacina Descontinuada IT', false)", gato);

        mvc.perform(get("/api/v1/especies/" + gato + "/vacinas").with(comPapel("VENDAS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nome", hasItem("Tríplice Felina (V3)")))
                .andExpect(jsonPath("$[*].nome", not(hasItem("Vacina Descontinuada IT"))))
                .andExpect(jsonPath("$[*].nome", everyItem(not(startsWith("V8")))));
    }

    @Test
    void especieInexistenteResponde404() throws Exception {
        mvc.perform(get("/api/v1/especies/999999/racas").with(comPapel("VENDAS"))).andExpect(status().isNotFound());
    }

    @Test
    void exigeAutenticacao() throws Exception {
        mvc.perform(get("/api/v1/especies")).andExpect(status().isUnauthorized());
    }
}
