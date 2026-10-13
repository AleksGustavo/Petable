package br.com.tecnodreamer.petconnect.shared.web;

import br.com.tecnodreamer.petconnect.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasLength;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RequestIdIT extends IntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void deveEcoarIdRecebido() throws Exception {
        mvc.perform(get("/actuator/health").header("X-Request-Id", "req-do-next-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "req-do-next-123"));
    }

    @Test
    void deveGerarQuandoAusenteMesmoEm401() throws Exception {
        mvc.perform(get("/api/v1/clientes"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", hasLength(36)));
    }
}
