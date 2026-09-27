package br.com.tecnodreamer.petconnect.agendamento.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgendamentoStatusControllerIT extends AgendaIntegrationTest {

    private long novo() throws Exception {
        return criarAgendamento(novoPet("Cão"), servicoId("Banho"), em(5, 9, 0));
    }

    @Test
    void vendasDeveConfirmar() throws Exception {
        long id = novo();
        mudarStatus(id, "VENDAS", statusJson("CONFIRMADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMADO"));
        assertThat(acoesAuditadas("agendamento", id)).contains("AGENDAMENTO_STATUS_ALTERADO");
    }

    @Test
    void servicoNaoDeveConfirmar() throws Exception {
        mudarStatus(novo(), "SERVICO", statusJson("CONFIRMADO")).andExpect(status().isForbidden());
    }

    @Test
    void servicoDeveIniciarConfirmado() throws Exception {
        long id = novo();
        mudarStatus(id, "ADMIN", statusJson("CONFIRMADO")).andExpect(status().isOk());
        mudarStatus(id, "SERVICO", statusJson("EM_ANDAMENTO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ANDAMENTO"));
    }

    @Test
    void servicoDeveConcluirEmAndamento() throws Exception {
        long id = novo();
        emAndamento(id);
        mudarStatus(id, "SERVICO", "{\"status\": \"CONCLUIDO\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                .andExpect(jsonPath("$.realizado.valor").value(60.0));
    }

    @Test
    void vendasDeveCancelar() throws Exception {
        long id = novo();
        mudarStatus(id, "VENDAS", "{\"status\": \"CANCELADO\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.motivo").exists());
        mudarStatus(id, "VENDAS", "{\"status\": \"CANCELADO\", \"motivo\": \"Tutor pediu\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"))
                .andExpect(jsonPath("$.observacoes").value("IT\n[CANCELAMENTO] Tutor pediu"));
        assertThat(acoesAuditadas("agendamento", id)).contains("AGENDAMENTO_CANCELADO");
    }

    @Test
    void servicoNaoDeveCancelar() throws Exception {
        mudarStatus(novo(), "SERVICO", "{\"status\": \"CANCELADO\", \"motivo\": \"Tutor pediu\"}")
                .andExpect(status().isForbidden());
    }

    @Test
    void servicoDeveMarcarNaoComparecimento() throws Exception {
        long id = novo();
        mudarStatus(id, "SERVICO", statusJson("NAO_COMPARECEU"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("ainda não atingiu")));

        jdbc.update("update agendamento set data_hora = ? where id = ?",
                java.time.LocalDateTime.now(FUSO).minusHours(1), id);
        mudarStatus(id, "SERVICO", statusJson("NAO_COMPARECEU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NAO_COMPARECEU"));
        assertThat(acoesAuditadas("agendamento", id)).contains("AGENDAMENTO_NAO_COMPARECEU");
    }

    @Test
    void deveRetornar409ParaTransicaoInvalida() throws Exception {
        mudarStatus(novo(), "ADMIN", statusJson("CONCLUIDO"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A transição de status AGENDADO para CONCLUIDO não é permitida."));
    }

    @Test
    void deveCriarServicoRealizadoAoConcluir() throws Exception {
        long id = novo();
        emAndamento(id);
        mudarStatus(id, "VENDAS", "{\"status\": \"CONCLUIDO\", \"valorFinal\": 48.5, \"observacoesConclusao\": \"Ok\"}")
                .andExpect(status().isOk());

        var linha = jdbc.queryForMap("select valor, observacoes, pet_id from servico_realizado where agendamento_id = ?", id);
        assertThat(linha.get("valor").toString()).isEqualTo("48.50");
        assertThat(linha.get("observacoes")).isEqualTo("Ok");
        assertThat(jdbc.queryForObject("select total_estimado from agendamento where id = ?", String.class, id))
                .isEqualTo("60.00");
        assertThat(acoesAuditadas("agendamento", id)).contains("AGENDAMENTO_CONCLUIDO");
    }

    @Test
    void deveImpedirSegundaConclusao() throws Exception {
        long id = novo();
        emAndamento(id);
        mudarStatus(id, "ADMIN", statusJson("CONCLUIDO")).andExpect(status().isOk());
        mudarStatus(id, "ADMIN", statusJson("CONCLUIDO")).andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("select count(*) from servico_realizado where agendamento_id = ?", Integer.class, id))
                .isEqualTo(1);
    }
}
