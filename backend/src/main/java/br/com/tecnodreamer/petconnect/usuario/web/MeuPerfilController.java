package br.com.tecnodreamer.petconnect.usuario.web;

import br.com.tecnodreamer.petconnect.funcionario.application.FuncionarioService;
import br.com.tecnodreamer.petconnect.funcionario.web.FuncionarioResponse;
import br.com.tecnodreamer.petconnect.funcionario.web.MeuFuncionarioRequest;
import br.com.tecnodreamer.petconnect.shared.security.UsuarioAtual;
import br.com.tecnodreamer.petconnect.usuario.application.TrocaSenhaService;
import br.com.tecnodreamer.petconnect.usuario.application.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Perfil do próprio usuário: a identidade vem sempre do token, nunca de um id na URL. */
@RestController
@RequestMapping("/api/v1/me")
public class MeuPerfilController {

    private final UsuarioService usuarios;
    private final FuncionarioService funcionarios;
    private final TrocaSenhaService trocaSenha;

    public MeuPerfilController(UsuarioService usuarios, FuncionarioService funcionarios, TrocaSenhaService trocaSenha) {
        this.usuarios = usuarios;
        this.funcionarios = funcionarios;
        this.trocaSenha = trocaSenha;
    }

    public record PerfilResponse(Long id, String username, String email, String papel, FuncionarioResponse funcionario) {
    }

    private static Long eu() {
        return UsuarioAtual.id().orElseThrow(() -> new InsufficientAuthenticationException("Sessão sem usuário"));
    }

    @GetMapping
    public PerfilResponse consultar() {
        return perfil(eu());
    }

    @PutMapping
    public PerfilResponse atualizar(@Valid @RequestBody ContaRequest request) {
        Long id = eu();
        usuarios.atualizarConta(id, request, "PERFIL_ATUALIZADO");
        return perfil(id);
    }

    @PutMapping("/funcionario")
    public FuncionarioResponse atualizarFuncionario(@Valid @RequestBody MeuFuncionarioRequest request) {
        return funcionarios.atualizarDoUsuario(eu(), request);
    }

    @PostMapping("/trocar-senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void trocarSenha(@Valid @RequestBody TrocaSenhaRequest request) {
        trocaSenha.trocar(eu(), request);
    }

    private PerfilResponse perfil(Long id) {
        var u = usuarios.buscar(id);
        return new PerfilResponse(u.id(), u.username(), u.email(), u.papel(), funcionarios.doUsuario(id).orElse(null));
    }
}
