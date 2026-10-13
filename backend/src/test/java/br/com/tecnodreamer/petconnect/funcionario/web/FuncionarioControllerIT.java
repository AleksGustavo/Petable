package br.com.tecnodreamer.petconnect.funcionario.web;

import br.com.tecnodreamer.petconnect.pet.web.PetIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FuncionarioControllerIT extends PetIntegrationTest {

    private static final String URL = "/api/v1/funcionarios";

    private static String json(long usuarioId, String cpf) {
        return """
                {"usuarioId": %d, "nome": "Ana Souza", "cpf": "%s", "dataNascimento": "1995-04-20", "sexo": "FEMININO",
                 "telefone": "%s", "endereco": {"cep": "13610-000", "logradouro": "Rua Exemplo", "numero": "100",
                 "bairro": "Centro", "cidade": "Leme", "uf": "sp"}}""".formatted(usuarioId, cpf, telefoneAleatorio());
    }

    @Test
    void adminDeveCriarFuncionario() throws Exception {
        long usuarioId = criarUsuario(nomeUsuario(), "SERVICO");
        String cpf = cpfAleatorio();

        mvc.perform(post(URL).with(comPapel("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(json(usuarioId, cpf)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuarioId").value(usuarioId))
                .andExpect(jsonPath("$.cpf").value(cpf))
                .andExpect(jsonPath("$.endereco.uf").value("SP"))
                .andExpect(jsonPath("$.disponivel").value(true));
        long id = jdbc.queryForObject("select id from funcionario where usuario_id = ?", Long.class, usuarioId);
        assertThat(auditoria.findByEntidadeAndEntidadeId("funcionario", String.valueOf(id),
                org.springframework.data.domain.Pageable.unpaged()).map(e -> e.acao()).toList()).contains("FUNCIONARIO_CRIADO");
    }

    @Test
    void vendasNaoDeveCriarFuncionario() throws Exception {
        mvc.perform(post(URL).with(comPapel("VENDAS")).contentType(MediaType.APPLICATION_JSON)
                .content(json(criarUsuario(nomeUsuario(), "VENDAS"), cpfAleatorio()))).andExpect(status().isForbidden());
    }

    @Test
    void deveRetornar409ParaUsuarioJaVinculado() throws Exception {
        long usuarioId = criarUsuario(nomeUsuario(), "SERVICO");
        criarFuncionario(usuarioId, "Primeiro");

        mvc.perform(post(URL).with(comPapel("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(json(usuarioId, cpfAleatorio())))
                .andExpect(status().isConflict());
    }

    @Test
    void deveRetornar400ParaCpfInvalido() throws Exception {
        mvc.perform(post(URL).with(comPapel("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(json(criarUsuario(nomeUsuario(), "SERVICO"), "12345678900")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.cpf").exists());
    }

    @Test
    void deveListarFuncionariosAtivosParaAgendamento() throws Exception {
        String nome = "Tosador " + nomeUsuario();
        long usuarioId = criarUsuario(nomeUsuario(), "SERVICO");
        criarFuncionario(usuarioId, nome);

        mvc.perform(get(URL).with(comPapel("SERVICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nome", hasItem(nome)));
        mvc.perform(get(URL).param("q", nome).with(comPapel("VENDAS")))
                .andExpect(jsonPath("$[0].usuarioId").value(usuarioId));
    }

    @Test
    void naoDeveListarFuncionarioDeUsuarioInativoComoDisponivel() throws Exception {
        String nome = "Inativo " + nomeUsuario();
        long usuarioId = criarUsuario(nomeUsuario(), "SERVICO");
        long funcionarioId = criarFuncionario(usuarioId, nome);
        mvc.perform(patch("/api/v1/usuarios/" + usuarioId + "/desativar").with(comPapel("ADMIN"))).andExpect(status().isOk());

        mvc.perform(get(URL).with(comPapel("VENDAS"))).andExpect(jsonPath("$[*].nome", not(hasItem(nome))));
        mvc.perform(get(URL + "/" + funcionarioId).with(comPapel("ADMIN")))
                .andExpect(jsonPath("$.disponivel").value(false));
    }
}
