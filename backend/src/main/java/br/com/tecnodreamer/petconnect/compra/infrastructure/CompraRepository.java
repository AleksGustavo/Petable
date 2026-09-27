package br.com.tecnodreamer.petconnect.compra.infrastructure;

import br.com.tecnodreamer.petconnect.compra.domain.Compra;
import br.com.tecnodreamer.petconnect.compra.domain.StatusCompra;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Optional;

public interface CompraRepository extends JpaRepository<Compra, Long> {

    @EntityGraph(attributePaths = {"fornecedor", "funcionario"})
    @Query("""
            select c from Compra c
            where (:status is null or c.status = :status)
              and (:fornecedorId is null or c.fornecedor.id = :fornecedorId)
              and (:inicio is null or c.dataCompra >= :inicio)
              and (:fim is null or c.dataCompra <= :fim)
            """)
    Page<Compra> buscar(StatusCompra status, Long fornecedorId, LocalDate inicio, LocalDate fim, Pageable pageable);

    @EntityGraph(attributePaths = {"fornecedor", "funcionario", "itens", "itens.produto"})
    Optional<Compra> findWithItensById(Long id);

    /** Trava a compra: dois recebimentos simultâneos não dão entrada duas vezes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Compra c where c.id = :id")
    Optional<Compra> travar(Long id);

    boolean existsByFornecedorIdAndStatus(Long fornecedorId, StatusCompra status);
}
