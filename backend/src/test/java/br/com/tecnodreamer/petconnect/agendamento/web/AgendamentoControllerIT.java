package br.com.tecnodreamer.petconnect.agendamento.web;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgendamentoControllerIT extends AgendaIntegrationTest {

    @Test
    void deveCriarAgendamentoComoVendas() throws Exception {
        long petId = novoPet("Cão");

        String body = postarAgendamento(petId, servicoId("Banho"), em(3, 9, 0))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("AGENDADO"))
                // Pet de porte GRANDE: preço do porte (V2) e não o padrão.
                .andExpect(jsonPath("$.totalEstimado").value(60.0))
                .andExpect(jsonPath("$.fimPrevisto").value(em(3, 9, 45).toString() + ":00"))
                .andReturn().getResponse().getContentAsString();

        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        assertThat(acoesAuditadas("agendamento", id)).contains("AGENDAMENTO_CRIADO");
    }

    @Test
    void servicoNaoDeveCriarAgendamento() throws Exception {
        long petId = novoPet("Cão");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(URL)
                        .with(comPapel("SERVICO")).contentType(MediaType.APPLICATION_JSON)
                        .content(agendamentoJson(petId, servicoId("Banho"), em(3, 9, 0))))
                .andExpect(status().isForbidden());
    }

    @Test
    void deveListarAgendaDoDia() throws Exception {
        long petId = novoPet("Cão");
        criarAgendamento(petId, servicoId("Tosa"), em(4, 14, 0));
        criarAgendamento(petId, servicoId("Banho"), em(4, 9, 0));
        criarAgendamento(petId, servicoId("Banho"), em(5, 9, 0));

        mvc.perform(get(URL).param("inicio", dia(4).toString()).param("fim", dia(4).toString())
                        .param("petId", String.valueOf(petId)).with(comPapel("SERVICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].servico.nome").value("Banho"))
                .andExpect(jsonPath("$.content[1].servico.nome").value("Tosa"));
    }

    @Test
    void deveListarIntervaloSemanal() throws Exception {
        long petId = novoPet("Cão");
        criarAgendamento(petId, servicoId("Banho"), em(3, 9, 0));
        criarAgendamento(petId, servicoId("Banho"), em(9, 9, 0));

        mvc.perform(get(URL).param("inicio", dia(3).toString()).param("fim", dia(9).toString())
                        .param("petId", String.valueOf(petId)).with(comPapel("VENDAS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));

        mvc.perform(get(URL).param("inicio", dia(3).toString()).param("fim", dia(10).toString()).with(comPapel("VENDAS")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fim").exists());
        mvc.perform(get(URL).param("inicio", dia(3).toString()).param("fim", dia(2).toString()).with(comPapel("VENDAS")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveFiltrarPorStatus() throws Exception {
        long petId = novoPet("Cão");
        long confirmado = criarAgendamento(petId, servicoId("Banho"), em(6, 9, 0));
        criarAgendamento(petId, servicoId("Banho"), em(6, 11, 0));
        mudarStatus(confirmado, "VENDAS", statusJson("CONFIRMADO")).andExpect(status().isOk());

        mvc.perform(get(URL).param("inicio", dia(6).toString()).param("fim", dia(6).toString())
                        .param("petId", String.valueOf(petId)).param("status", "CONFIRMADO").with(comPapel("VENDAS")))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(confirmado));
    }

    @Test
    void deveFiltrarPorPet() throws Exception {
        long petA = novoPet("Cão");
        long petB = novoPet("Gato");
        criarAgendamento(petA, servicoId("Banho"), em(7, 9, 0));
        criarAgendamento(petB, servicoId("Banho"), em(7, 9, 0));

        mvc.perform(get(URL).param("inicio", dia(7).toString()).param("fim", dia(7).toString())
                        .param("petId", String.valueOf(petB)).with(comPapel("VENDAS")))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].pet.id").value(petB));
    }

    @Test
    void deveFiltrarPorTutor() throws Exception {
        long clienteId = criarCliente(unico("Tutor Filtro"));
        long pet1 = criarPet(clienteId, "Um", "Cão");
        long pet2 = criarPet(clienteId, "Dois", "Cão");
        criarAgendamento(pet1, servicoId("Banho"), em(8, 9, 0));
        criarAgendamento(pet2, servicoId("Banho"), em(8, 9, 0));
        criarAgendamento(novoPet("Cão"), servicoId("Banho"), em(8, 9, 0));

        mvc.perform(get(URL).param("inicio", dia(8).toString()).param("fim", dia(8).toString())
                        .param("clienteId", String.valueOf(clienteId)).with(comPapel("VENDAS")))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].tutor.id").value(clienteId));
    }

    @Test
    void deveBuscarPorNomeDoPet() throws Exception {
        String nome = unico("Bisteca");
        long petId = criarPet(criarCliente(unico("Tutor Busca Pet")), nome, "Cão");
        criarAgendamento(petId, servicoId("Banho"), em(10, 9, 0));

        mvc.perform(get(URL).param("inicio", dia(10).toString()).param("fim", dia(10).toString())
                        .param("q", nome.toUpperCase()).with(comPapel("SERVICO")))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].pet.nome").value(nome));
    }

    @Test
    void deveBuscarPorNomeDoTutor() throws Exception {
        String tutor = unico("Genoveva Agenda");
        long petId = criarPet(criarCliente(tutor), "Tico", "Cão");
        criarAgendamento(petId, servicoId("Banho"), em(10, 15, 0));

        mvc.perform(get(URL).param("inicio", dia(10).toString()).param("fim", dia(10).toString())
                        .param("q", tutor.toLowerCase()).with(comPapel("SERVICO")))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].tutor.nome").value(tutor));
    }

    @Test
    void deveRetornarPrecoPrevisto() throws Exception {
        long petId = novoPet("Cão");

        mvc.perform(get(URL + "/preco").param("petId", String.valueOf(petId))
                        .param("servicoId", String.valueOf(servicoId("Banho e Tosa"))).with(comPapel("SERVICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.porte").value("GRANDE"))
                .andExpect(jsonPath("$.origemPreco").value("PORTE"))
                .andExpect(jsonPath("$.totalEstimado").value(130.0))
                .andExpect(jsonPath("$.duracaoMinutos").value(90));

        mvc.perform(get(URL + "/preco").param("petId", String.valueOf(petId))
                        .param("servicoId", String.valueOf(servicoId("Hidratação"))).with(comPapel("SERVICO")))
                .andExpect(jsonPath("$.origemPreco").value("PADRAO"))
                .andExpect(jsonPath("$.totalEstimado").value(25.0));
    }

    @Test
    void deveRetornar409ParaConflitoDoPet() throws Exception {
        long petId = novoPet("Cão");
        criarAgendamento(petId, servicoId("Banho"), em(11, 9, 0));

        postarAgendamento(petId, servicoId("Tosa"), em(11, 9, 30))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("O pet já possui um agendamento que se sobrepõe a este horário."));
    }

    @Test
    void devePermitirHorarioNoFimExatoDoAnterior() throws Exception {
        long petId = novoPet("Cão");
        criarAgendamento(petId, servicoId("Banho"), em(12, 9, 0));

        // Banho dura 45 min (V2): 09:45 começa exatamente no fim do anterior.
        postarAgendamento(petId, servicoId("Tosa"), em(12, 9, 45)).andExpect(status().isCreated());
    }

    @Test
    void deveEditarAgendamento() throws Exception {
        long petId = novoPet("Cão");
        long id = criarAgendamento(petId, servicoId("Banho"), em(13, 9, 0));

        mvc.perform(put(URL + "/" + id).with(comPapel("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(agendamentoJson(petId, servicoId("Banho e Tosa"), em(13, 10, 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servico.nome").value("Banho e Tosa"))
                .andExpect(jsonPath("$.totalEstimado").value(130.0))
                .andExpect(jsonPath("$.status").value("AGENDADO"));

        assertThat(acoesAuditadas("agendamento", id)).contains("AGENDAMENTO_ATUALIZADO");
    }

    @Test
    void deveRetornar409AoEditarStatusNaoPermitido() throws Exception {
        long petId = novoPet("Cão");
        long id = criarAgendamento(petId, servicoId("Banho"), em(14, 9, 0));
        emAndamento(id);

        mvc.perform(put(URL + "/" + id).with(comPapel("VENDAS")).contentType(MediaType.APPLICATION_JSON)
                        .content(agendamentoJson(petId, servicoId("Banho"), em(14, 11, 0))))
                .andExpect(status().isConflict());
    }

    @Test
    void dataNoPassadoEPetInexistenteRespondem400() throws Exception {
        long petId = novoPet("Cão");
        postarAgendamento(petId, servicoId("Banho"), em(-1, 9, 0))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.dataHora").exists());
        postarAgendamento(999_999, servicoId("Banho"), em(3, 9, 0))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.petId").exists());
    }

    @Test
    void proximosAgendamentosDoPet() throws Exception {
        long petId = novoPet("Cão");
        long depois = criarAgendamento(petId, servicoId("Banho"), em(20, 9, 0));
        long antes = criarAgendamento(petId, servicoId("Banho"), em(15, 9, 0));
        long cancelado = criarAgendamento(petId, servicoId("Banho"), em(16, 9, 0));
        mudarStatus(cancelado, "VENDAS", "{\"status\": \"CANCELADO\", \"motivo\": \"Desistiu\"}")
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/pets/" + petId + "/agendamentos/proximos").with(comPapel("SERVICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(antes))
                .andExpect(jsonPath("$[1].id").value(depois));
        mvc.perform(get("/api/v1/pets/" + petId + "/agendamentos/proximos").param("limit", "21").with(comPapel("SERVICO")))
                .andExpect(status().isBadRequest());
    }
}
