package br.com.tecnodreamer.petconnect.auth.web;

import br.com.tecnodreamer.petconnect.auth.application.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final br.com.tecnodreamer.petconnect.auth.application.LimitadorLogin limitador;

    public AuthController(AuthService authService, br.com.tecnodreamer.petconnect.auth.application.LimitadorLogin limitador) {
        this.authService = authService;
        this.limitador = limitador;
    }

    /** O IP vem do X-Forwarded-For repassado pelo Next; a API não é exposta à internet (DA-15). */
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, jakarta.servlet.http.HttpServletRequest http) {
        String ip = br.com.tecnodreamer.petconnect.usuario.web.RecuperacaoSenhaController.ipDoCliente(http);
        java.util.Optional<Long> bloqueio = limitador.bloqueio(ip);
        if (bloqueio.isPresent()) {
            log.warn("LOGIN_LIMITADO por IP; nova tentativa em {}s", bloqueio.get());
            throw new br.com.tecnodreamer.petconnect.shared.error.LimiteExcedidoException(
                    "Muitas tentativas de login deste endereço. Tente novamente em alguns minutos.", bloqueio.get());
        }
        try {
            return authService.login(request);
        } catch (org.springframework.security.authentication.BadCredentialsException e) {
            limitador.falhou(ip);
            throw e;
        }
    }

    /** Papel e username atuais (vêm do banco, via conversor do JWT), não os da emissão do token. */
    @GetMapping("/me")
    public UsuarioAtual me(@AuthenticationPrincipal Jwt jwt, Authentication auth) {
        Number uid = jwt.getClaim("uid");
        String papel = auth.getAuthorities().stream().map(a -> a.getAuthority().replaceFirst("^ROLE_", ""))
                .findFirst().orElse(null);
        return new UsuarioAtual(uid == null ? null : uid.longValue(), auth.getName(), papel);
    }

    public record UsuarioAtual(Long id, String username, String papel) {
    }
}
