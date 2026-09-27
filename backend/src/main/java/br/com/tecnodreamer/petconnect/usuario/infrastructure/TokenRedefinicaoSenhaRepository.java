package br.com.tecnodreamer.petconnect.usuario.infrastructure;

import br.com.tecnodreamer.petconnect.usuario.domain.TokenRedefinicaoSenha;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;

public interface TokenRedefinicaoSenhaRepository extends JpaRepository<TokenRedefinicaoSenha, Long> {

    @EntityGraph(attributePaths = {"usuario", "usuario.papel"})
    Optional<TokenRedefinicaoSenha> findByTokenHash(String tokenHash);

    /** Invalida (marca como usados) todos os pedidos ainda pendentes do usuário. */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("update TokenRedefinicaoSenha t set t.usadoEm = :agora where t.usuario.id = :usuarioId and t.usadoEm is null")
    int invalidarPendentes(Long usuarioId, LocalDateTime agora);
}
