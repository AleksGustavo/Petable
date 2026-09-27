package br.com.tecnodreamer.petconnect.venda.infrastructure;

import br.com.tecnodreamer.petconnect.venda.domain.FormaPagamento;
import br.com.tecnodreamer.petconnect.venda.domain.StatusVenda;
import br.com.tecnodreamer.petconnect.venda.domain.Venda;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public interface VendaRepository extends JpaRepository<Venda, Long> {

    /** Período semiaberto [inicio, fim) em horário local; datas nulas não filtram. */
    @EntityGraph(attributePaths = {"cliente", "usuario"})
    @Query(value = """
            select v from Venda v
            where (:status is null or v.status = :status)
              and (:forma is null or v.formaPagamento = :forma)
              and (:clienteId is null or v.cliente.id = :clienteId)
              and (:inicio is null or v.dataVenda >= :inicio)
              and (:fim is null or v.dataVenda < :fim)
            """, countQuery = """
            select count(v) from Venda v
            where (:status is null or v.status = :status)
              and (:forma is null or v.formaPagamento = :forma)
              and (:clienteId is null or v.cliente.id = :clienteId)
              and (:inicio is null or v.dataVenda >= :inicio)
              and (:fim is null or v.dataVenda < :fim)
            """)
    Page<Venda> buscar(StatusVenda status, FormaPagamento forma, Long clienteId, LocalDateTime inicio, LocalDateTime fim,
            Pageable pageable);

    @EntityGraph(attributePaths = {"cliente", "funcionario", "usuario", "canceladaPor", "itens", "itens.produto"})
    Optional<Venda> findWithItensById(Long id);

    @EntityGraph(attributePaths = {"cliente", "funcionario", "usuario", "canceladaPor", "itens", "itens.produto"})
    Optional<Venda> findByChaveIdempotencia(String chaveIdempotencia);

    /** Trava a venda: dois cancelamentos simultâneos não estornam duas vezes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Venda v where v.id = :id")
    Optional<Venda> travar(Long id);

    /** Agregação no banco (dashboard): só vendas concluídas no período. */
    @Query("""
            select count(v) as quantidade, sum(v.valorTotal) as total from Venda v
            where v.status = br.com.tecnodreamer.petconnect.venda.domain.StatusVenda.CONCLUIDA
              and v.dataVenda >= :inicio and v.dataVenda < :fim
            """)
    Resumo resumir(LocalDateTime inicio, LocalDateTime fim);

    interface Resumo {
        Long getQuantidade();

        BigDecimal getTotal();
    }
}
