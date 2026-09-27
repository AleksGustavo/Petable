package br.com.tecnodreamer.petconnect.pet.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HistoricoConsultaControllerIT extends PetIntegrationTest {

    private final LocalDateTime agora = LocalDateTime.now(ZoneId.of("America/Sao_Paulo")).truncatedTo(ChronoUnit.MINUTES);

    private static String consulta(LocalDateTime data, String veterinario) {
        return """
                {"dataConsulta": "%s", "veterinario": "%s", "motivo": "Retorno"}""".formatted(data, veterinario);
    }

    @Test
    void adminVendasEServicoRegistramEListaVemDaMaisRecente() throws Exception {
        long petId = criarPet(criarCliente(unico("Tutor Consulta")), "Luna", "Gato");
        String url = "/api/v1/pets/" + petId + "/consultas";

        mvc.perform(post(url).with(comPapel("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content(consulta(agora.minusDays(30), "Dra. Admin"))).andExpect(status().isCreated());
        mvc.perform(post(url).with(comPapel("VENDAS")).contentType(MediaType.APPLICATION_JSON)
                .content(consulta(agora.minusDays(1), "Dr. Vendas"))).andExpect(status().isCreated());
        mvc.perform(post(url).with(comPapel("SERVICO")).contentType(MediaType.APPLICATION_JSON)
                        .content(consulta(agora.minusDays(10), "Dra. Servico")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.veterinario").value("Dra. Servico"));

        mvc.perform(get(url).with(comPapel("SERVICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].veterinario").value("Dr. Vendas"))
                .andExpect(jsonPath("$[2].veterinario").value("Dra. Admin"));

        assertThat(acoesAuditadas(petId)).filteredOn("CONSULTA_PET_REGISTRADA"::equals).hasSize(3);
    }

    @Test
    void consultaFuturaOuSemVeterinarioResponde400() throws Exception {
        long petId = criarPet(criarCliente(unico("Tutor Consulta Invalida")), "Nina", "Cão");
        String url = "/api/v1/pets/" + petId + "/consultas";

        mvc.perform(post(url).with(comPapel("VENDAS")).contentType(MediaType.APPLICATION_JSON)
                        .content(consulta(agora.plusDays(1), "Dr. Futuro")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.dataConsulta").exists());
        mvc.perform(post(url).with(comPapel("VENDAS")).contentType(MediaType.APPLICATION_JSON)
                        .content(consulta(agora.minusDays(1), " ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.veterinario").exists());
    }

    @Test
    void petInativoResponde404() throws Exception {
        long petId = criarPet(criarCliente(unico("Tutor Pet Inativo")), "Fumaça", "Cão");
        mvc.perform(delete("/api/v1/pets/" + petId).with(comPapel("VENDAS"))).andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/pets/" + petId + "/consultas").with(comPapel("SERVICO"))
                        .contentType(MediaType.APPLICATION_JSON).content(consulta(agora.minusDays(1), "Dr. X")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/pets/" + petId + "/consultas").with(comPapel("SERVICO")))
                .andExpect(status().isNotFound());
    }
}
