package br.com.tecnodreamer.petconnect.auth.application;

import br.com.tecnodreamer.petconnect.auth.web.LoginRequest;
import br.com.tecnodreamer.petconnect.auth.web.TokenResponse;
import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.usuario.domain.Usuario;
import br.com.tecnodreamer.petconnect.usuario.infrastructure.UsuarioRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Service
public class AuthService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final LoginAttemptLimiter limiter;
    private final AuditoriaService auditoria;
    /** Compara mesmo quando o usuário não existe, para o tempo de resposta não revelar isso. */
    private final String hashFalso;

    public AuthService(UsuarioRepository usuarios, PasswordEncoder encoder, TokenService tokens,
            LoginAttemptLimiter limiter, AuditoriaService auditoria) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.tokens = tokens;
        this.limiter = limiter;
        this.auditoria = auditoria;
        this.hashFalso = encoder.encode("senha-que-nunca-sera-usada");
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest req) {
        if (limiter.bloqueado(req.login())) {
            throw new LockedException("Muitas tentativas de login. Tente novamente em alguns minutos.");
        }
        Optional<Usuario> encontrado = usuarios.findByLogin(req.login().trim());
        boolean senhaOk = encoder.matches(req.senha(), encontrado.map(Usuario::getSenhaHash).orElse(hashFalso));
        if (encontrado.isEmpty() || !senhaOk || !encontrado.get().isAtivo()) {
            limiter.falhou(req.login());
            auditoria.registrar(req.login(), "LOGIN_FALHOU", "usuario", null, Map.of());
            throw new BadCredentialsException("credenciais inválidas");
        }
        Usuario u = encontrado.get();
        limiter.sucesso(req.login());
        auditoria.registrar(u.getUsername(), "LOGIN", "usuario", u.getId(), Map.of());
        return new TokenResponse(tokens.emitir(u), "Bearer", tokens.validadeEmSegundos(),
                new TokenResponse.UsuarioLogado(u.getId(), u.getUsername(), u.getEmail(), u.getPapel().getNome()));
    }
}
