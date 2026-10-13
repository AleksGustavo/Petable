package br.com.tecnodreamer.petconnect.auth.web;

import br.com.tecnodreamer.petconnect.IntegrationTest;
import br.com.tecnodreamer.petconnect.auth.application.LimitadorLogin;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoginRateLimitIT extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired LimitadorLogin limitador;

    private ResultActions login(String ip, String senha) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").header("X-Forwarded-For", ip + ", 10.0.0.1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\": \"%s\", \"senha\": \"%s\"}".formatted(ADMIN_USER, senha)));
    }

    @Test
    void falhaDeveContarPorIpEBloquearCom429() throws Exception {
        login("203.0.113.50", "senha-errada").andExpect(status().isUnauthorized());
        for (int i = 0; i < 19; i++) {
            limitador.falhou("203.0.113.50");
        }
        login("203.0.113.50", ADMIN_PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.title").value("Muitas tentativas"));
    }

    @Test
    void outroIpContinuaEntrando() throws Exception {
        for (int i = 0; i < 25; i++) {
            limitador.falhou("203.0.113.60");
        }
        login("203.0.113.61", ADMIN_PASSWORD).andExpect(status().isOk());
    }
}
