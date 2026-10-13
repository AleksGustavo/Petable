package br.com.tecnodreamer.petconnect.pet.web;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CarteiraVacinaControllerIT extends PetIntegrationTest {

    private final LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    private String lancamento(long vacinaId, LocalDate aplicacao, LocalDate proxima) {
        return """
                {"vacinaId": %d, "dataAplicacao": "%s", "dataProxima": %s, "veterinario": "Dra. Ana", "lote": "L1"}"""
                .formatted(vacinaId, aplicacao, proxima == null ? "null" : "\"" + proxima + "\"");
    }

    @Test
    void registraListaComStatusERemove() throws Exception {
        long petId = criarPet(criarCliente(unico("Tutor Vacina")), "Rex", "Cão");
        String url = "/api/v1/pets/" + petId + "/vacinas";
        long raiva = vacinaId("Cão", "Raiva (Antirrábica Canina)");
        long v8 = vacinaId("Cão", "V8 Polivalente");

        String body = mvc.perform(post(url).with(comPapel("VENDAS")).contentType(MediaType.APPLICATION_JSON)
                        .content(lancamento(raiva, hoje.minusYears(1), hoje.minusDays(3))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("VENCIDA"))
                .andExpect(jsonPath("$.statusDescricao").value("Vencida"))
                .andReturn().getResponse().getContentAsString();
        long lancamentoId = ((Number) JsonPath.read(body, "$.id")).longValue();

        mvc.perform(post(url).with(comPapel("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(lancamento(v8, hoje, hoje.plusYears(1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("EM_DIA"));

        mvc.perform(get(url).with(comPapel("SERVICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].vacina.nome").value("V8 Polivalente"))
                .andExpect(jsonPath("$[1].status").value("VENCIDA"));

        mvc.perform(delete(url + "/" + lancamentoId).with(comPapel("VENDAS"))).andExpect(status().isNoContent());
        mvc.perform(get(url).with(comPapel("VENDAS"))).andExpect(jsonPath("$", hasSize(1)));

        assertThat(acoesAuditadas(petId))
                .contains("VACINA_PET_REGISTRADA", "VACINA_PET_REMOVIDA");
    }

    @Test
    void vacinaDeOutraEspecieResponde409() throws Exception {
        long petId = criarPet(criarCliente(unico("Tutor Gato")), "Mingau", "Gato");

        mvc.perform(post("/api/v1/pets/" + petId + "/vacinas").with(comPapel("VENDAS"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lancamento(vacinaId("Cão", "V8 Polivalente"), hoje, null)))
                .andExpect(status().isConflict());
    }

    @Test
    void datasInvalidasRespondem400NoCampo() throws Exception {
        long petId = criarPet(criarCliente(unico("Tutor Datas")), "Bolt", "Cão");
        String url = "/api/v1/pets/" + petId + "/vacinas";
        long raiva = vacinaId("Cão", "Raiva (Antirrábica Canina)");

        mvc.perform(post(url).with(comPapel("VENDAS")).contentType(MediaType.APPLICATION_JSON)
                        .content(lancamento(raiva, hoje.plusDays(1), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.dataAplicacao").exists());
        mvc.perform(post(url).with(comPapel("VENDAS")).contentType(MediaType.APPLICATION_JSON)
                        .content(lancamento(raiva, hoje, hoje.minusDays(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.dataProxima").exists());
    }

    @Test
    void naoRemoveLancamentoDeOutroPetEServicoNaoLanca() throws Exception {
        long clienteId = criarCliente(unico("Tutor Dois Pets"));
        long petA = criarPet(clienteId, "A", "Cão");
        long petB = criarPet(clienteId, "B", "Cão");
        String body = mvc.perform(post("/api/v1/pets/" + petA + "/vacinas").with(comPapel("VENDAS"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lancamento(vacinaId("Cão", "V8 Polivalente"), hoje, null)))
                .andExpect(jsonPath("$.status").value("SEM_PROXIMA_DOSE"))
                .andReturn().getResponse().getContentAsString();
        long lancamentoA = ((Number) JsonPath.read(body, "$.id")).longValue();

        mvc.perform(delete("/api/v1/pets/" + petB + "/vacinas/" + lancamentoA).with(comPapel("VENDAS")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/pets/" + petA + "/vacinas").with(comPapel("SERVICO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lancamento(vacinaId("Cão", "V8 Polivalente"), hoje, null)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/pets/" + petA + "/vacinas/" + lancamentoA).with(comPapel("SERVICO")))
                .andExpect(status().isForbidden());
    }
}
