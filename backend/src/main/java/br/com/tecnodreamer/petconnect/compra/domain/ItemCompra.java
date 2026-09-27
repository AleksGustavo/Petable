package br.com.tecnodreamer.petconnect.compra.domain;

import br.com.tecnodreamer.petconnect.produto.domain.Produto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Item da compra. {@code subtotal} é coluna GERADA no MySQL (V1); aqui é só
 * calculado em Java, com a mesma fórmula, para não depender de refresh.
 */
@Entity
@Table(name = "item_compra")
public class ItemCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "compra_id")
    private Compra compra;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id")
    private Produto produto;

    private int quantidade;

    @Column(name = "preco_custo_unitario")
    private BigDecimal precoCustoUnitario;

    protected ItemCompra() {
    }

    ItemCompra(Compra compra, Produto produto, int quantidade, BigDecimal precoCustoUnitario) {
        this.compra = compra;
        this.produto = produto;
        this.quantidade = quantidade;
        this.precoCustoUnitario = precoCustoUnitario;
    }

    public BigDecimal subtotal() {
        return precoCustoUnitario.multiply(BigDecimal.valueOf(quantidade)).setScale(2, RoundingMode.HALF_UP);
    }

    public Long getId() {
        return id;
    }

    public Produto getProduto() {
        return produto;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public BigDecimal getPrecoCustoUnitario() {
        return precoCustoUnitario;
    }
}
