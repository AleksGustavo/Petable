package br.com.tecnodreamer.petconnect.usuario.infrastructure;

import br.com.tecnodreamer.petconnect.usuario.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    @Query("select u from Usuario u where lower(u.username) = lower(:login) or lower(u.email) = lower(:login)")
    Optional<Usuario> findByLogin(String login);

    boolean existsByPapelNome(String papel);
}
