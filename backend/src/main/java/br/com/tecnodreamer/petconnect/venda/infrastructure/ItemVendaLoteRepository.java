package br.com.tecnodreamer.petconnect.venda.infrastructure;

import br.com.tecnodreamer.petconnect.venda.domain.ItemVendaLote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface ItemVendaLoteRepository extends JpaRepository<ItemVendaLote, Long> {

    List<ItemVendaLote> findByItemVendaIdOrderByIdAsc(Long itemVendaId);

    boolean existsByItemVendaId(Long itemVendaId);

    /** Rastreio lote → vendas (recolhimento). */
    List<ItemVendaLote> findByLoteIdOrderByIdAsc(Long loteId);

    /**
     * D13: itens de venda CONCLUIDA ainda cancelável (desde {@code desde}) deste produto que não têm alocação de lote.
     * Cancelar uma venda assim não teria lote de origem, então o controle de lote não pode ser ligado agora.
     */
    @Query("""
            select count(i) from ItemVenda i
            where i.produto.id = :produtoId and i.venda.status = br.com.tecnodreamer.petconnect.venda.domain.StatusVenda.CONCLUIDA
              and i.venda.dataVenda >= :desde
              and not exists (select 1 from ItemVendaLote l where l.itemVenda = i)
            """)
    long contarItensCancelaveisSemLote(Long produtoId, LocalDateTime desde);
}
