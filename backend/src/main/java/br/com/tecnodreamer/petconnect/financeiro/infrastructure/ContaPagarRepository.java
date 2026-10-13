package br.com.tecnodreamer.petconnect.financeiro.infrastructure;

import br.com.tecnodreamer.petconnect.financeiro.domain.ContaPagar;
import br.com.tecnodreamer.petconnect.financeiro.domain.StatusConta;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ContaPagarRepository extends JpaRepository<ContaPagar, Long> {

    Optional<ContaPagar> findByCompraId(Long compraId);

    @EntityGraph(attributePaths = {"compra", "compra.fornecedor", "usuarioPagamento"})
    Optional<ContaPagar> findWithDetalhesById(Long id);

    /** Trava a conta: dois pagamentos simultâneos, só um passa (DA-11). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ContaPagar c where c.id = :id")
    Optional<ContaPagar> travar(Long id);

    /** Vencimento em [venceDe, venceAte]; situação resolvida pelo serviço em intervalos de data. */
    @EntityGraph(attributePaths = {"compra", "compra.fornecedor"})
    @Query("""
            select c from ContaPagar c
            where (:status is null or c.status = :status)
              and (:venceDe is null or c.dataVencimento >= :venceDe)
              and (:venceAte is null or c.dataVencimento <= :venceAte)
            """)
    Page<ContaPagar> buscar(StatusConta status, LocalDate venceDe, LocalDate venceAte, Pageable pageable);

    interface Resumo {
        Long getQuantidade();

        BigDecimal getTotal();
    }

    @Query("""
            select count(c) as quantidade, sum(c.saldo) as total from ContaPagar c
            where c.status = br.com.tecnodreamer.petconnect.financeiro.domain.StatusConta.ABERTA
              and c.dataVencimento >= :de and c.dataVencimento <= :ate
            """)
    Resumo resumirAbertas(LocalDate de, LocalDate ate);

    @Query(value = """
            select c.id from compra c where c.status = 'RECEBIDO' and not exists
              (select 1 from conta_pagar p where p.compra_id = c.id)
              and not exists (select 1 from migracao_legado_mapa m where m.entidade = 'compra' and m.id_novo = c.id)
            order by c.id
            """, nativeQuery = true)
    List<Long> comprasRecebidasSemConta();
}
