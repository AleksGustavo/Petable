package br.com.tecnodreamer.petconnect.agendamento.web;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgendamentoVacinaIT extends AgendaIntegrationTest {

    private String conclusao(long vacinaId) {
        LocalDate hoje = LocalDate.now(FUSO);
        return """
                {"status": "CONCLUIDO", "valorFinal": 80.0, "observacoesConclusao": "Sem intercorrências",
                 "vacina": {"vacinaId": %d, "dataAplicacao": "%s", "dataProxima": "%s", "veterinario": "Dra. Ana"}}"""
                .formatted(vacinaId, hoje, hoje.plusYears(1));
    }

    private long vacinacaoEmAndamento(long petId) throws Exception {
        long id = criarAgendamento(petId, servicoVacinacaoId(), em(4, 8, 0));
        emAndamento(id);
        return id;
    }

    @Test
    void deveConcluirAgendamentoDeVacinacaoELancarDoseNaCarteira() throws Exception {
        long petId = novoPet("Cão");
        long id = vacinacaoEmAndamento(petId);

        mudarStatus(id, "SERVICO", conclusao(vacinaId("Cão", "Raiva (Antirrábica Canina)")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                .andExpect(jsonPath("$.servico.vacinacao").value(true));

        mvc.perform(get("/api/v1/pets/" + petId + "/vacinas").with(comPapel("SERVICO")))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].vacina.nome").value("Raiva (Antirrábica Canina)"))
                .andExpect(jsonPath("$[0].status").value("EM_DIA"));
        assertThat(acoesAuditadas(petId)).contains("VACINA_PET_REGISTRADA");
        assertThat(acoesAuditadas("agendamento", id)).contains("AGENDAMENTO_CONCLUIDO");
    }

    @Test
    void deveRetornar409ParaVacinaDeOutraEspecie() throws Exception {
        long id = vacinacaoEmAndamento(novoPet("Cão"));

        mudarStatus(id, "VENDAS", conclusao(vacinaId("Gato", "Tríplice Felina (V3)")))
                .andExpect(status().isConflict());
    }

    @Test
    void deveManterAgendamentoEmAndamentoQuandoVacinaFalhar() throws Exception {
        long id = vacinacaoEmAndamento(novoPet("Cão"));

        mudarStatus(id, "VENDAS", conclusao(vacinaId("Gato", "Tríplice Felina (V3)"))).andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("select status from agendamento where id = ?", String.class, id))
                .isEqualTo("EM_ANDAMENTO");
        mudarStatus(id, "VENDAS", "{\"status\": \"CONCLUIDO\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.vacina").exists());
    }

    @Test
    void deveNaoCriarServicoRealizadoQuandoVacinaFalhar() throws Exception {
        long petId = novoPet("Cão");
        long id = vacinacaoEmAndamento(petId);

        mudarStatus(id, "VENDAS", conclusao(vacinaId("Gato", "Tríplice Felina (V3)"))).andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("select count(*) from servico_realizado where agendamento_id = ?", Integer.class, id))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from carteira_vacina where pet_id = ?", Integer.class, petId))
                .isZero();
    }

    @Test
    void payloadDeVacinaEmServicoComumResponde400() throws Exception {
        long id = criarAgendamento(novoPet("Cão"), servicoId("Banho"), em(4, 10, 0));
        emAndamento(id);

        mudarStatus(id, "VENDAS", conclusao(vacinaId("Cão", "V8 Polivalente")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.vacina").exists());
    }
}
