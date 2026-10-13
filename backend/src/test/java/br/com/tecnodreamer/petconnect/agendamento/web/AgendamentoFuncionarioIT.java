package br.com.tecnodreamer.petconnect.agendamento.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgendamentoFuncionarioIT extends AgendaIntegrationTest {

    private static String comFuncionario(long petId, long servicoId, long funcionarioId, java.time.LocalDateTime dataHora) {
        return """
                {"petId": %d, "servicoId": %d, "funcionarioId": %d, "dataHora": "%s"}"""
                .formatted(petId, servicoId, funcionarioId, dataHora);
    }

    private long funcionarioNovo() throws Exception {
        return criarFuncionario(criarUsuario(nomeUsuario(), "SERVICO"), "Tosadora " + nomeUsuario());
    }

    @Test
    void deveCriarAgendamentoComFuncionarioAtivo() throws Exception {
        long funcionarioId = funcionarioNovo();

        mvc.perform(post(URL).with(comPapel("VENDAS")).contentType(MediaType.APPLICATION_JSON)
                        .content(comFuncionario(novoPet("Cão"), servicoId("Banho"), funcionarioId, em(5, 9, 0))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.funcionario.id").value(funcionarioId))
                .andExpect(jsonPath("$.funcionario.disponivel").value(true));
    }

    @Test
    void deveRejeitarFuncionarioComUsuarioInativo() throws Exception {
        long usuarioId = criarUsuario(nomeUsuario(), "SERVICO");
        long funcionarioId = criarFuncionario(usuarioId, "Inativa " + nomeUsuario());
        mvc.perform(patch("/api/v1/usuarios/" + usuarioId + "/desativar").with(comPapel("ADMIN"))).andExpect(status().isOk());

        mvc.perform(post(URL).with(comPapel("VENDAS")).contentType(MediaType.APPLICATION_JSON)
                        .content(comFuncionario(novoPet("Cão"), servicoId("Banho"), funcionarioId, em(5, 9, 0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("O funcionário selecionado não está disponível para novos agendamentos."));
    }

    @Test
    void deveManterFuncionarioEmAgendamentoExistenteAposDesativacao() throws Exception {
        long usuarioId = criarUsuario(nomeUsuario(), "SERVICO");
        long funcionarioId = criarFuncionario(usuarioId, "Desligada " + nomeUsuario());
        String body = mvc.perform(post(URL).with(comPapel("VENDAS")).contentType(MediaType.APPLICATION_JSON)
                        .content(comFuncionario(novoPet("Cão"), servicoId("Banho"), funcionarioId, em(6, 9, 0))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.id")).longValue();

        mvc.perform(patch("/api/v1/usuarios/" + usuarioId + "/desativar").with(comPapel("ADMIN"))).andExpect(status().isOk());

        mvc.perform(get(URL + "/" + id).with(comPapel("VENDAS")))
                .andExpect(jsonPath("$.status").value("AGENDADO"))
                .andExpect(jsonPath("$.funcionario.id").value(funcionarioId))
                .andExpect(jsonPath("$.funcionario.disponivel").value(false));
    }

    @Test
    void deveDetectarConflitoDeAgendaDoFuncionarioAtivo() throws Exception {
        long funcionarioId = funcionarioNovo();
        mvc.perform(post(URL).with(comPapel("VENDAS")).contentType(MediaType.APPLICATION_JSON)
                        .content(comFuncionario(novoPet("Cão"), servicoId("Banho"), funcionarioId, em(7, 9, 0))))
                .andExpect(status().isCreated());

        mvc.perform(post(URL).with(comPapel("VENDAS")).contentType(MediaType.APPLICATION_JSON)
                        .content(comFuncionario(novoPet("Gato"), servicoId("Tosa"), funcionarioId, em(7, 9, 30))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("O funcionário já possui um agendamento que se sobrepõe a este horário."));
    }
}
