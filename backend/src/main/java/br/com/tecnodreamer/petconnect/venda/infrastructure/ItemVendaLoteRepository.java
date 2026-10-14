package br.com.tecnodreamer.petconnect.venda.infrastructure;

import br.com.tecnodreamer.petconnect.venda.domain.ItemVendaLote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface ItemVendaLoteRepository extends JpaRepository<ItemVendaLote, Long> {

    List<ItemVendaLote> findByItemVendaIdOrderByIdAsc(Long itemVendaId);

    boolean existsByItemVendaId(Long itemVendaId);

    /** Alocações dos itens de uma venda, com o lote junto (detalhe da venda). */
    @Query("select a from ItemVendaLote a join fetch a.lote where a.itemVenda.id in :itemIds order by a.id")
    List<ItemVendaLote> dosItens(java.util.Collection<Long> itemIds);

    /** Saídas de um lote com a venda junto (rastreabilidade). */
    @Query("select a from ItemVendaLote a join fetch a.itemVenda iv join fetch iv.venda where a.lote.id = :loteId order by a.id")
    List<ItemVendaLote> rastreio(Long loteId);

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
