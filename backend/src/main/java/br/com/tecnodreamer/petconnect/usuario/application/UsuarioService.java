package br.com.tecnodreamer.petconnect.usuario.application;

import br.com.tecnodreamer.petconnect.funcionario.domain.Funcionario;
import br.com.tecnodreamer.petconnect.funcionario.infrastructure.FuncionarioRepository;
import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;
import br.com.tecnodreamer.petconnect.shared.error.ConflictException;
import br.com.tecnodreamer.petconnect.shared.error.NotFoundException;
import br.com.tecnodreamer.petconnect.usuario.domain.Papel;
import br.com.tecnodreamer.petconnect.usuario.domain.PoliticaSenha;
import br.com.tecnodreamer.petconnect.usuario.domain.Usuario;
import br.com.tecnodreamer.petconnect.usuario.infrastructure.PapelRepository;
import br.com.tecnodreamer.petconnect.usuario.infrastructure.UsuarioRepository;
import br.com.tecnodreamer.petconnect.usuario.web.ContaRequest;
import br.com.tecnodreamer.petconnect.usuario.web.UsuarioRequest;
import br.com.tecnodreamer.petconnect.usuario.web.UsuarioResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UsuarioService {

    static final String ENTIDADE = "usuario";
    static final String ULTIMO_ADMIN = "Não é possível remover ou desativar o último administrador ativo do sistema.";

    private final UsuarioRepository usuarios;
    private final PapelRepository papeis;
    private final FuncionarioRepository funcionarios;
    private final PasswordEncoder encoder;
    private final AuditoriaService auditoria;

    public UsuarioService(UsuarioRepository usuarios, PapelRepository papeis, FuncionarioRepository funcionarios,
            PasswordEncoder encoder, AuditoriaService auditoria) {
        this.usuarios = usuarios;
        this.papeis = papeis;
        this.funcionarios = funcionarios;
        this.encoder = encoder;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Page<UsuarioResponse> listar(String busca, Boolean ativo, String papel, Pageable pageable) {
        String termo = busca == null || busca.isBlank() ? null : busca.trim();
        Page<Usuario> pagina = usuarios.buscar(termo, ativo, papel, pageable);
        Map<Long, Funcionario> porUsuario = funcionarios
                .findByUsuarioIdIn(pagina.map(Usuario::getId).getContent()).stream()
                .collect(Collectors.toMap(f -> f.getUsuario().getId(), Function.identity()));
        return pagina.map(u -> UsuarioResponse.of(u, porUsuario.get(u.getId())));
    }

    @Transactional(readOnly = true)
    public UsuarioResponse buscar(Long id) {
        return resposta(carregar(id));
    }

    @Transactional
    public UsuarioResponse criar(UsuarioRequest req) {
        String username = req.username().trim();
        String email = req.email().trim().toLowerCase();
        exigirContaUnica(username, email, null);
        PoliticaSenha.violacao(req.senha(), username, email).ifPresent(msg -> {
            throw new CampoInvalidoException("senha", msg);
        });
        Usuario salvo = usuarios.saveAndFlush(new Usuario(username, email, encoder.encode(req.senha()), papel(req.papel())));
        auditoria.registrar("USUARIO_CRIADO", ENTIDADE, salvo.getId(), Map.of("username", username, "papel", req.papel()));
        return UsuarioResponse.of(salvo, null);
    }

    /** Conta de qualquer usuário (ADMIN) ou do próprio (perfil): a ação de auditoria diz qual. */
    @Transactional
    public UsuarioResponse atualizarConta(Long id, ContaRequest req, String acaoAuditoria) {
        Usuario u = carregar(id);
        String username = req.username().trim();
        String email = req.email().trim().toLowerCase();
        exigirContaUnica(username, email, id);
        u.alterarConta(username, email);
        usuarios.saveAndFlush(u);
        auditoria.registrar(acaoAuditoria, ENTIDADE, id, Map.of("username", username));
        return resposta(u);
    }

    @Transactional
    public UsuarioResponse alterarPapel(Long id, String papelNome, Long solicitanteId) {
        if (id.equals(solicitanteId)) {
            throw new ConflictException("Você não pode alterar o seu próprio papel.");
        }
        Usuario u = usuarios.travar(id).orElseThrow(() -> new NotFoundException("Usuário", id));
        Papel novo = papel(papelNome);
        String anterior = u.getPapel().getNome();
        if (u.isAdmin() && u.isAtivo() && !Papel.ADMIN.equals(novo.getNome())) {
            exigirOutroAdminAtivo(u);
        }
        if (u.alterarPapel(novo)) {
            auditoria.registrar("USUARIO_PAPEL_ALTERADO", ENTIDADE, id,
                    Map.of("papelAnterior", anterior, "papelNovo", novo.getNome()));
        }
        return resposta(u);
    }

    @Transactional
    public UsuarioResponse desativar(Long id, Long solicitanteId) {
        if (id.equals(solicitanteId)) {
            throw new ConflictException("Você não pode desativar a sua própria conta.");
        }
        Usuario u = usuarios.travar(id).orElseThrow(() -> new NotFoundException("Usuário", id));
        if (u.isAtivo()) {
            if (u.isAdmin()) {
                exigirOutroAdminAtivo(u);
            }
            u.desativar();
            auditoria.registrar("USUARIO_DESATIVADO", ENTIDADE, id, Map.of("username", u.getUsername()));
        }
        return resposta(u);
    }

    @Transactional
    public UsuarioResponse ativar(Long id) {
        Usuario u = usuarios.travar(id).orElseThrow(() -> new NotFoundException("Usuário", id));
        if (!u.isAtivo()) {
            u.ativar();
            auditoria.registrar("USUARIO_ATIVADO", ENTIDADE, id, Map.of("username", u.getUsername()));
        }
        return resposta(u);
    }

    /** Trava (FOR UPDATE) os ADMINs ativos: dois rebaixamentos simultâneos não deixam o sistema sem ADMIN. */
    private void exigirOutroAdminAtivo(Usuario alvo) {
        boolean existeOutro = usuarios.travarAdminsAtivos().stream().anyMatch(a -> !a.getId().equals(alvo.getId()));
        if (!existeOutro) {
            throw new ConflictException(ULTIMO_ADMIN);
        }
    }

    private void exigirContaUnica(String username, String email, Long ignorarId) {
        boolean usernameEmUso = ignorarId == null ? usuarios.existsByUsernameIgnoreCase(username)
                : usuarios.existsByUsernameIgnoreCaseAndIdNot(username, ignorarId);
        if (usernameEmUso) {
            throw new ConflictException("Este nome de usuário já está em uso.");
        }
        boolean emailEmUso = ignorarId == null ? usuarios.existsByEmailIgnoreCase(email)
                : usuarios.existsByEmailIgnoreCaseAndIdNot(email, ignorarId);
        if (emailEmUso) {
            throw new ConflictException("Este e-mail já está em uso.");
        }
    }

    private Papel papel(String nome) {
        return papeis.findByNome(nome).orElseThrow(() -> new CampoInvalidoException("papel", "Papel inválido."));
    }

    private Usuario carregar(Long id) {
        return usuarios.findById(id).orElseThrow(() -> new NotFoundException("Usuário", id));
    }

    private UsuarioResponse resposta(Usuario u) {
        return UsuarioResponse.of(u, funcionarios.findByUsuarioId(u.getId()).orElse(null));
    }
}
