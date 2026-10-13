package br.com.tecnodreamer.petconnect.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Correlação Next ↔ API (DA-11): usa o X-Request-Id recebido (se for um id bem
 * formado) ou gera um; põe no MDC (aparece em todo log da requisição) e devolve na resposta.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String CABECALHO = "X-Request-Id";
    public static final String MDC_CHAVE = "requestId";
    private static final Pattern VALIDO = Pattern.compile("[A-Za-z0-9-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = normalizar(request.getHeader(CABECALHO));
        MDC.put(MDC_CHAVE, id);
        response.setHeader(CABECALHO, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_CHAVE);
        }
    }

    static String normalizar(String recebido) {
        return recebido != null && VALIDO.matcher(recebido).matches() ? recebido : UUID.randomUUID().toString();
    }
}
