package br.com.tecnodreamer.petconnect.shared.modulos;

import br.com.tecnodreamer.petconnect.shared.modulos.CatalogoModulos.Resolucao;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Política central de módulo (etapa 07): TODA chamada autenticada a {@code /api/**} de um módulo não contratado é recusada aqui, antes da
 * autorização por papel e do controller, inclusive chamadas diretas por curl sem passar por menu. Roda depois da autenticação do token
 * (quem não tem token continua recebendo 401 e nada é revelado) e usa o caminho DECODIFICADO, o mesmo que o MVC roteia, para que uma
 * variação como {@code /api/v1/%66inanceiro} não escape. O módulo é somado ao papel: o acesso exige os dois.
 *
 * <p>Resposta uniforme: {@code 403} com corpo {@code application/problem+json} e {@code codigo = MODULO_INDISPONIVEL}, sem dizer qual
 * módulo (o 404 isolado não é fronteira de segurança, e a lista do que está contratado é a de {@code GET /api/v1/modulos}).
 */
public class ModulosFilter extends OncePerRequestFilter {

    static final String CORPO = "{\"type\":\"about:blank\",\"title\":\"Recurso indispon\u00edvel\",\"status\":403,"
            + "\"detail\":\"Este recurso n\u00e3o est\u00e1 dispon\u00edvel nesta instala\u00e7\u00e3o.\",\"codigo\":\"MODULO_INDISPONIVEL\"}";

    private static final Logger log = LoggerFactory.getLogger(ModulosFilter.class);

    private final ModulosAtivos modulos;
    private final UrlPathHelper caminhos = new UrlPathHelper();

    public ModulosFilter(ModulosAtivos modulos) {
        this.modulos = modulos;
        this.caminhos.setUrlDecode(true);
        this.caminhos.setRemoveSemicolonContent(true);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String caminho = caminhos.getPathWithinApplication(request);
        if (caminho.startsWith("/api/") && autenticado()) {
            Resolucao r = modulos.catalogo().resolver(request.getMethod(), caminho);
            if (!modulos.permite(r, caminho)) {
                log.info("Módulo indisponível: {} {} (dono {})", request.getMethod(), caminho, r.semDono() ? "-" : r.donos());
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getWriter().write(CORPO);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static boolean autenticado() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.isAuthenticated() && !(a instanceof AnonymousAuthenticationToken);
    }
}
