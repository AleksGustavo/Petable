package br.com.tecnodreamer.petconnect.usuario.infrastructure;

import br.com.tecnodreamer.petconnect.usuario.domain.Usuario;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    @Query("select u from Usuario u where lower(u.username) = lower(:login) or lower(u.email) = lower(:login)")
    Optional<Usuario> findByLogin(String login);

    boolean existsByPapelNome(String papel);

    Optional<Usuario> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCaseAndIdNot(String username, Long id);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    /** Busca por username, e-mail ou nome do funcionário vinculado. */
    @Query("""
            select u from Usuario u left join Funcionario f on f.usuario = u
            where (:ativo is null or u.ativo = :ativo)
              and (:papel is null or u.papel.nome = :papel)
              and (:termo is null
                   or lower(u.username) like lower(concat('%', :termo, '%'))
                   or lower(u.email) like lower(concat('%', :termo, '%'))
                   or lower(f.nome) like lower(concat('%', :termo, '%')))
            """)
    Page<Usuario> buscar(String termo, Boolean ativo, String papel, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.id = :id")
    Optional<Usuario> travar(Long id);

    /** Trava os ADMINs ativos: serializa a regra do "último ADMIN" (DA-04). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.papel.nome = 'ADMIN' and u.ativo = true")
    List<Usuario> travarAdminsAtivos();
}
