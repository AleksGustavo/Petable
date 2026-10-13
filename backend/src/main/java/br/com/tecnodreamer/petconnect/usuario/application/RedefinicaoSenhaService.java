package br.com.tecnodreamer.petconnect.usuario.application;

import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;
import br.com.tecnodreamer.petconnect.usuario.domain.PoliticaSenha;
import br.com.tecnodreamer.petconnect.usuario.domain.TokenRedefinicaoSenha;
import br.com.tecnodreamer.petconnect.usuario.domain.Usuario;
import br.com.tecnodreamer.petconnect.usuario.infrastructure.EmailService;
import br.com.tecnodreamer.petconnect.usuario.infrastructure.LimitadorRedefinicao;
import br.com.tecnodreamer.petconnect.usuario.infrastructure.TokenRedefinicaoSenhaRepository;
import br.com.tecnodreamer.petconnect.usuario.infrastructure.UsuarioRepository;
import br.com.tecnodreamer.petconnect.usuario.web.RedefinirSenhaRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

/**
 * "Esqueci a senha" e redefinição por token de uso único. A resposta pública
 * nunca revela se o e-mail existe, se a conta está inativa ou se o limite de
 * pedidos foi atingido (DA-09/DA-10).
 */
@Service
public class RedefinicaoSenhaService {

    private static final Logger log = LoggerFactory.getLogger(RedefinicaoSenhaService.class);
    static final Duration VALIDADE = Duration.ofMinutes(30);
    static final String TOKEN_INVALIDO = "O link de redefinição é inválido ou expirou.";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UsuarioRepository usuarios;
    private final TokenRedefinicaoSenhaRepository tokens;
    private final PasswordEncoder encoder;
    private final EmailService email;
    private final LimitadorRedefinicao limitador;
    private final AuditoriaService auditoria;
    private final Clock clock;
    private final String frontendUrl;

    public RedefinicaoSenhaService(UsuarioRepository usuarios, TokenRedefinicaoSenhaRepository tokens,
            PasswordEncoder encoder, EmailService email, LimitadorRedefinicao limitador, AuditoriaService auditoria,
            Clock clock, @Value("${app.frontend-url}") String frontendUrl) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.encoder = encoder;
        this.email = email;
        this.limitador = limitador;
        this.auditoria = auditoria;
        this.clock = clock;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    @Transactional
    public void solicitar(String emailInformado, String ip) {
        if (!limitador.permitir(emailInformado, ip)) {
            return;
        }
        Optional<Usuario> encontrado = usuarios.findByEmailIgnoreCase(emailInformado.trim()).filter(Usuario::isAtivo);
        if (encontrado.isEmpty()) {
            return;
        }
        Usuario u = encontrado.get();
        LocalDateTime agora = LocalDateTime.now(clock);
        tokens.invalidarPendentes(u.getId(), agora);
        String token = gerarToken();
        tokens.save(new TokenRedefinicaoSenha(u, sha256(token), agora.plus(VALIDADE)));
        try {
            email.enviar(u.getEmail(), "PetConnect Web — redefinição de senha", """
                    Olá, %s.

                    Recebemos um pedido para redefinir a sua senha. Para criar uma nova, acesse o link abaixo
                    (válido por 30 minutos e utilizável uma única vez):

                    %s/redefinir-senha?token=%s

                    Se você não fez este pedido, ignore este e-mail: a sua senha atual continua valendo.
                    """.formatted(u.getUsername(), frontendUrl, token));
        } catch (RuntimeException e) {
            // Falha de SMTP não muda a resposta pública (continua 202).
            log.error("Falha ao enviar e-mail de redefinição para o usuário {}", u.getId(), e);
        }
        auditoria.registrar(u.getUsername(), "REDEFINICAO_SENHA_SOLICITADA", UsuarioService.ENTIDADE, u.getId(), Map.of());
    }

    @Transactional
    public void redefinir(RedefinirSenhaRequest req) {
        if (!req.novaSenha().equals(req.confirmacaoNovaSenha())) {
            throw new CampoInvalidoException("confirmacaoNovaSenha", "A confirmação não confere com a nova senha.");
        }
        LocalDateTime agora = LocalDateTime.now(clock);
        TokenRedefinicaoSenha t = tokens.findByTokenHash(sha256(req.token()))
                .filter(x -> x.utilizavel(agora) && x.getUsuario().isAtivo())
                .orElseThrow(() -> new CampoInvalidoException("token", TOKEN_INVALIDO));
        Usuario u = t.getUsuario();
        PoliticaSenha.violacao(req.novaSenha(), u.getUsername(), u.getEmail()).ifPresent(msg -> {
            throw new CampoInvalidoException("novaSenha", msg);
        });
        u.definirSenha(encoder.encode(req.novaSenha()));
        t.marcarUsado(agora);
        usuarios.save(u);
        tokens.save(t);
        tokens.invalidarPendentes(u.getId(), agora);
        auditoria.registrar(u.getUsername(), "SENHA_REDEFINIDA", UsuarioService.ENTIDADE, u.getId(), Map.of());
    }

    /** 32 bytes aleatórios em Base64 URL-safe (43 caracteres). */
    static String gerarToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String valor) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
