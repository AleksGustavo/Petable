package br.com.tecnodreamer.petconnect.compra.infrastructure;

import br.com.tecnodreamer.petconnect.compra.domain.ItemCompraLote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemCompraLoteRepository extends JpaRepository<ItemCompraLote, Long> {

    List<ItemCompraLote> findByItemCompraIdOrderByIdAsc(Long itemCompraId);

    /** Rastreio lote → entradas. */
    List<ItemCompraLote> findByLoteIdOrderByIdAsc(Long loteId);
}
