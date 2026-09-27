package br.com.tecnodreamer.petconnect.financeiro.infrastructure;

import br.com.tecnodreamer.petconnect.financeiro.domain.Caixa;
import br.com.tecnodreamer.petconnect.financeiro.domain.StatusCaixa;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;

public interface CaixaRepository extends JpaRepository<Caixa, Long> {

    @EntityGraph(attributePaths = "usuario")
    Optional<Caixa> findByUsuarioIdAndStatus(Long usuarioId, StatusCaixa status);

    /**
     * Trava o caixa ABERTO do usuário. Usado por venda, serviço, pagamento, manual,
     * sangria/suprimento e fechamento: um fechamento concorrente espera e, ao liberar,
     * a linha já não está ABERTA (a leitura com lock vê o dado atual).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Caixa c where c.usuario.id = :usuarioId and c.status = br.com.tecnodreamer.petconnect.financeiro.domain.StatusCaixa.ABERTO")
    Optional<Caixa> travarAbertoDoUsuario(Long usuarioId);

    @EntityGraph(attributePaths = "usuario")
    @Query("""
            select c from Caixa c
            where (:status is null or c.status = :status)
              and (:usuarioId is null or c.usuario.id = :usuarioId)
              and (:inicio is null or c.abertoEm >= :inicio)
              and (:fim is null or c.abertoEm < :fim)
            """)
    Page<Caixa> buscar(StatusCaixa status, Long usuarioId, LocalDateTime inicio, LocalDateTime fim, Pageable pageable);

    @EntityGraph(attributePaths = "usuario")
    Optional<Caixa> findWithUsuarioById(Long id);

    long countByStatus(StatusCaixa status);
}
