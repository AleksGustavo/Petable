package br.com.tecnodreamer.petconnect.venda.infrastructure;

import br.com.tecnodreamer.petconnect.venda.domain.ItemVendaLote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemVendaLoteRepository extends JpaRepository<ItemVendaLote, Long> {

    List<ItemVendaLote> findByItemVendaIdOrderByIdAsc(Long itemVendaId);

    /** Rastreio lote → vendas (recolhimento). */
    List<ItemVendaLote> findByLoteIdOrderByIdAsc(Long loteId);
}
