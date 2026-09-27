package br.com.tecnodreamer.petconnect.usuario.application;

import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;
import br.com.tecnodreamer.petconnect.shared.error.NotFoundException;
import br.com.tecnodreamer.petconnect.usuario.domain.PoliticaSenha;
import br.com.tecnodreamer.petconnect.usuario.domain.Usuario;
import br.com.tecnodreamer.petconnect.usuario.infrastructure.TokenRedefinicaoSenhaRepository;
import br.com.tecnodreamer.petconnect.usuario.infrastructure.UsuarioRepository;
import br.com.tecnodreamer.petconnect.usuario.web.TrocaSenhaRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

@Service
public class TrocaSenhaService {

    private final UsuarioRepository usuarios;
    private final TokenRedefinicaoSenhaRepository tokens;
    private final PasswordEncoder encoder;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public TrocaSenhaService(UsuarioRepository usuarios, TokenRedefinicaoSenhaRepository tokens, PasswordEncoder encoder,
            AuditoriaService auditoria, Clock clock) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.encoder = encoder;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    /** Troca a própria senha; a sessão atual e as demais deixam de valer (token_version++). */
    @Transactional
    public void trocar(Long usuarioId, TrocaSenhaRequest req) {
        Usuario u = usuarios.findById(usuarioId).orElseThrow(() -> new NotFoundException("Usuário", usuarioId));
        if (!encoder.matches(req.senhaAtual(), u.getSenhaHash())) {
            throw new CampoInvalidoException("senhaAtual", "A senha atual está incorreta.");
        }
        if (!req.novaSenha().equals(req.confirmacaoNovaSenha())) {
            throw new CampoInvalidoException("confirmacaoNovaSenha", "A confirmação não confere com a nova senha.");
        }
        if (encoder.matches(req.novaSenha(), u.getSenhaHash())) {
            throw new CampoInvalidoException("novaSenha", "A nova senha deve ser diferente da atual.");
        }
        PoliticaSenha.violacao(req.novaSenha(), u.getUsername(), u.getEmail()).ifPresent(msg -> {
            throw new CampoInvalidoException("novaSenha", msg);
        });
        u.definirSenha(encoder.encode(req.novaSenha()));
        usuarios.save(u);
        tokens.invalidarPendentes(usuarioId, LocalDateTime.now(clock));
        auditoria.registrar(u.getUsername(), "SENHA_ALTERADA", UsuarioService.ENTIDADE, usuarioId, Map.of());
    }
}
