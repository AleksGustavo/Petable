package br.com.tecnodreamer.petconnect.compra.infrastructure;

import br.com.tecnodreamer.petconnect.compra.domain.ItemCompraLote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemCompraLoteRepository extends JpaRepository<ItemCompraLote, Long> {

    List<ItemCompraLote> findByItemCompraIdOrderByIdAsc(Long itemCompraId);

    /** Lotes recebidos nos itens de uma compra (detalhe da compra). */
    @org.springframework.data.jpa.repository.Query("select i from ItemCompraLote i where i.itemCompra.id in :itemIds order by i.id")
    List<ItemCompraLote> dosItens(java.util.Collection<Long> itemIds);

    /** Entradas de um lote com a compra e o fornecedor juntos (rastreabilidade). */
    @org.springframework.data.jpa.repository.Query("""
            select i from ItemCompraLote i join fetch i.itemCompra ic join fetch ic.compra c join fetch c.fornecedor
            where i.lote.id = :loteId order by i.id
            """)
    List<ItemCompraLote> rastreio(Long loteId);

    /** Rastreio lote → entradas. */
    List<ItemCompraLote> findByLoteIdOrderByIdAsc(Long loteId);
}
