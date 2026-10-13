package br.com.tecnodreamer.petconnect.usuario.web;

import br.com.tecnodreamer.petconnect.pet.web.PetIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MeuPerfilControllerIT extends PetIntegrationTest {

    private static final String NOVA = "Nova@Senha2026";

    private String troca(String atual, String nova) {
        return """
                {"senhaAtual": "%s", "novaSenha": "%s", "confirmacaoNovaSenha": "%s"}""".formatted(atual, nova, nova);
    }

    @Test
    void usuarioDeveConsultarProprioPerfil() throws Exception {
        String username = nomeUsuario();
        long id = criarUsuario(username, "VENDAS");
        String token = login(username, SENHA);

        mvc.perform(get("/api/v1/me").with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.papel").value("VENDAS"))
                .andExpect(jsonPath("$.funcionario").doesNotExist());
    }

    @Test
    void usuarioDeveEditarUsernameEEmail() throws Exception {
        String username = nomeUsuario();
        long id = criarUsuario(username, "VENDAS");
        String token = login(username, SENHA);
        String novo = nomeUsuario();

        mvc.perform(put("/api/v1/me").with(bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"%s\", \"email\": \"%s@petconnect.test\"}".formatted(novo, novo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(novo));
        tentarLogin(novo + "@petconnect.test", SENHA).andExpect(status().isOk());
        assertThat(acoesAuditadas("usuario", id)).contains("PERFIL_ATUALIZADO");
    }

    @Test
    void usuarioNaoDeveAlterarProprioPapel() throws Exception {
        String username = nomeUsuario();
        long id = criarUsuario(username, "VENDAS");
        String token = login(username, SENHA);

        mvc.perform(put("/api/v1/me").with(bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"%s\", \"email\": \"%s@petconnect.test\", \"papel\": \"ADMIN\"}"
                                .formatted(username, username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.papel").value("VENDAS"));
        mvc.perform(patch("/api/v1/usuarios/" + id + "/papel").with(bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"papel\": \"ADMIN\"}")).andExpect(status().isForbidden());
    }

    @Test
    void funcionarioDeveEditarPropriosDadosPermitidos() throws Exception {
        String username = nomeUsuario();
        long usuarioId = criarUsuario(username, "SERVICO");
        criarFuncionario(usuarioId, "Nome Antigo");
        String cpf = jdbc.queryForObject("select cpf from funcionario where usuario_id = ?", String.class, usuarioId);
        String token = login(username, SENHA);

        mvc.perform(put("/api/v1/me/funcionario").with(bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Nome Novo", "sexo": "OUTRO", "telefone": "%s", "cpf": "11144477735"}"""
                                .formatted(telefoneAleatorio())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Nome Novo"))
                .andExpect(jsonPath("$.cpf").value(cpf))
                .andExpect(jsonPath("$.endereco").doesNotExist());
        mvc.perform(get("/api/v1/me").with(bearer(token))).andExpect(jsonPath("$.funcionario.nome").value("Nome Novo"));
    }

    @Test
    void usuarioSemFuncionarioDeveReceber404AoEditarFuncionario() throws Exception {
        String username = nomeUsuario();
        criarUsuario(username, "VENDAS");

        mvc.perform(put("/api/v1/me/funcionario").with(bearer(login(username, SENHA))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\": \"Qualquer\"}")).andExpect(status().isNotFound());
    }

    @Test
    void deveTrocarSenha() throws Exception {
        String username = nomeUsuario();
        long id = criarUsuario(username, "VENDAS");
        String token = login(username, SENHA);

        mvc.perform(post("/api/v1/me/trocar-senha").with(bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(troca("Errada@Senha2026", NOVA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.senhaAtual").exists());
        mvc.perform(post("/api/v1/me/trocar-senha").with(bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(troca(SENHA, NOVA))).andExpect(status().isNoContent());
        assertThat(acoesAuditadas("usuario", id)).contains("SENHA_ALTERADA");
    }

    @Test
    void loginComSenhaAntigaDeveFalharAposTroca() throws Exception {
        String username = nomeUsuario();
        criarUsuario(username, "VENDAS");
        mvc.perform(post("/api/v1/me/trocar-senha").with(bearer(login(username, SENHA))).contentType(MediaType.APPLICATION_JSON)
                .content(troca(SENHA, NOVA))).andExpect(status().isNoContent());

        tentarLogin(username, SENHA).andExpect(status().isUnauthorized());
    }

    @Test
    void loginComSenhaNovaDeveFuncionar() throws Exception {
        String username = nomeUsuario();
        criarUsuario(username, "VENDAS");
        mvc.perform(post("/api/v1/me/trocar-senha").with(bearer(login(username, SENHA))).contentType(MediaType.APPLICATION_JSON)
                .content(troca(SENHA, NOVA))).andExpect(status().isNoContent());

        tentarLogin(username, NOVA).andExpect(status().isOk());
    }

    @Test
    void jwtAnteriorDeveFalharAposTrocaDeSenha() throws Exception {
        String username = nomeUsuario();
        criarUsuario(username, "VENDAS");
        String token = login(username, SENHA);
        mvc.perform(post("/api/v1/me/trocar-senha").with(bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(troca(SENHA, NOVA))).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/me").with(bearer(token))).andExpect(status().isUnauthorized());
    }
}
