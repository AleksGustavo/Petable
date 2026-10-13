package br.com.tecnodreamer.petconnect.shared.web;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    RequestIdFilter filtro = new RequestIdFilter();

    private String executar(String recebido, AtomicReference<String> noMdc) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        if (recebido != null) {
            req.addHeader("X-Request-Id", recebido);
        }
        MockHttpServletResponse res = new MockHttpServletResponse();
        filtro.doFilter(req, res, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest r, jakarta.servlet.ServletResponse s) {
                noMdc.set(MDC.get("requestId"));
            }
        });
        return res.getHeader("X-Request-Id");
    }

    @Test
    void deveGerarQuandoAusente() throws Exception {
        AtomicReference<String> mdc = new AtomicReference<>();
        String id = executar(null, mdc);
        assertThat(id).hasSize(36);
        assertThat(mdc.get()).isEqualTo(id);
    }

    @Test
    void deveAceitarIdValido() throws Exception {
        AtomicReference<String> mdc = new AtomicReference<>();
        assertThat(executar("abc12345-req", mdc)).isEqualTo("abc12345-req");
        assertThat(mdc.get()).isEqualTo("abc12345-req");
    }

    @Test
    void deveRejeitarIdInvalidoOuLongo() throws Exception {
        AtomicReference<String> mdc = new AtomicReference<>();
        assertThat(executar("<script>alert(1)</script>", mdc)).hasSize(36);
        assertThat(executar("a".repeat(65), mdc)).hasSize(36);
    }

    @Test
    void deveLimparMdcAoFinal() throws Exception {
        executar("abc12345-req", new AtomicReference<>());
        assertThat(MDC.get("requestId")).isNull();
    }
}
