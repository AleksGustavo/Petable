package br.com.tecnodreamer.petconnect.auth.web;

import br.com.tecnodreamer.petconnect.auth.application.AuthService;
import br.com.tecnodreamer.petconnect.shared.config.SecurityConfig;
import jakarta.validation.Valid;
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

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public UsuarioAtual me(@AuthenticationPrincipal Jwt jwt) {
        Number uid = jwt.getClaim("uid");
        return new UsuarioAtual(uid.longValue(), jwt.getSubject(),
                jwt.getClaimAsStringList(SecurityConfig.ROLES_CLAIM).getFirst());
    }

    public record UsuarioAtual(Long id, String username, String papel) {
    }
}
