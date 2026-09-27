package br.com.tecnodreamer.petconnect.usuario.web;

import br.com.tecnodreamer.petconnect.funcionario.domain.Funcionario;
import br.com.tecnodreamer.petconnect.usuario.domain.Usuario;

import java.time.Instant;

/** Nunca expõe senha_hash nem token_version. */
public record UsuarioResponse(Long id, String username, String email, String papel, boolean ativo,
        FuncionarioVinculado funcionario, Instant createdAt) {

    public record FuncionarioVinculado(Long id, String nome) {
    }

    public static UsuarioResponse of(Usuario u, Funcionario f) {
        return new UsuarioResponse(u.getId(), u.getUsername(), u.getEmail(), u.getPapel().getNome(), u.isAtivo(),
                f == null ? null : new FuncionarioVinculado(f.getId(), f.getNome()), u.getCreatedAt());
    }
}
