package br.com.tecnodreamer.petconnect.venda.domain;

import br.com.tecnodreamer.petconnect.produto.domain.LoteEstoque;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * Alocação FEFO de um item de venda (D8): quanto saiu de cada lote. Imutável; é a base do rastreio
 * lote → venda e do retorno ao lote original no cancelamento (D9). A soma por item é a quantidade do item (I5).
 */
@Entity
@Immutable
@Table(name = "item_venda_lote")
public class ItemVendaLote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_venda_id")
    private ItemVenda itemVenda;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lote_id")
    private LoteEstoque lote;

    private int quantidade;

    protected ItemVendaLote() {
    }

    public ItemVendaLote(ItemVenda itemVenda, LoteEstoque lote, int quantidade) {
        if (quantidade <= 0) {
            throw new IllegalArgumentException("A quantidade alocada deve ser positiva.");
        }
        this.itemVenda = itemVenda;
        this.lote = lote;
        this.quantidade = quantidade;
    }

    public Long getId() {
        return id;
    }

    public ItemVenda getItemVenda() {
        return itemVenda;
    }

    public LoteEstoque getLote() {
        return lote;
    }

    public int getQuantidade() {
        return quantidade;
    }
}
