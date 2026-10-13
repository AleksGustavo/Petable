package br.com.tecnodreamer.petconnect.shared.web;

import br.com.tecnodreamer.petconnect.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Em teste o management fica na mesma porta; em produção vai para a 9090 interna (application-prod.yml). */
class PrometheusIT extends IntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void deveExporMetricasDaJvm() throws Exception {
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm_memory_used_bytes")));
    }
}
