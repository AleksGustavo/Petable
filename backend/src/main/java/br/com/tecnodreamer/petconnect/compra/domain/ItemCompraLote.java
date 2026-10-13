package br.com.tecnodreamer.petconnect.compra.domain;

import br.com.tecnodreamer.petconnect.produto.domain.LoteEstoque;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Lote informado no recebimento de um item de compra (D17). Guarda o que veio na nota (código, validade,
 * quantidade); {@code lote} é ligado quando o recebimento vira movimento de estoque (etapa 03).
 */
@Entity
@Table(name = "item_compra_lote")
public class ItemCompraLote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_compra_id", updatable = false)
    private ItemCompra itemCompra;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_id")
    private LoteEstoque lote;

    @Column(name = "codigo_lote", updatable = false)
    private String codigoLote;

    @Column(updatable = false)
    private LocalDate validade;

    @Column(updatable = false)
    private int quantidade;

    protected ItemCompraLote() {
    }

    public ItemCompraLote(ItemCompra itemCompra, String codigoLote, LocalDate validade, int quantidade) {
        if (quantidade <= 0) {
            throw new IllegalArgumentException("A quantidade do lote deve ser positiva.");
        }
        this.itemCompra = itemCompra;
        this.codigoLote = LoteEstoque.normalizarCodigo(codigoLote);
        this.validade = validade;
        this.quantidade = quantidade;
    }

    /** Liga o lote físico criado ou somado no recebimento. */
    public void vincularLote(LoteEstoque lote) {
        this.lote = lote;
    }

    public Long getId() {
        return id;
    }

    public ItemCompra getItemCompra() {
        return itemCompra;
    }

    public LoteEstoque getLote() {
        return lote;
    }

    public String getCodigoLote() {
        return codigoLote;
    }

    public LocalDate getValidade() {
        return validade;
    }

    public int getQuantidade() {
        return quantidade;
    }
}
