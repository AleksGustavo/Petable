package br.com.tecnodreamer.petconnect.auth.web;

import br.com.tecnodreamer.petconnect.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerIT extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void loginValidoDevolveTokenQueAbreRotasProtegidas() throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login": "%s", "senha": "%s"}""".formatted(ADMIN_USER, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.usuario.papel").value("ADMIN"))
                .andReturn().getResponse().getContentAsString();
        String token = body.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(ADMIN_USER))
                .andExpect(jsonPath("$.papel").value("ADMIN"));
    }

    @Test
    void senhaErradaResponde401SemDizerOMotivo() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login": "%s", "senha": "errada"}""".formatted(ADMIN_USER)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Usuário ou senha inválidos."));
    }

    @Test
    void usuarioInexistenteRespondeIgualASenhaErrada() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login": "ninguem", "senha": "qualquer"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Usuário ou senha inválidos."));
    }

    @Test
    void tokenInvalidoResponde401() throws Exception {
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer nao.e.um.jwt"))
                .andExpect(status().isUnauthorized());
    }
}
