package br.com.tecnodreamer.petconnect.compra.domain;

import br.com.tecnodreamer.petconnect.fornecedor.domain.Fornecedor;
import br.com.tecnodreamer.petconnect.funcionario.domain.Funcionario;
import br.com.tecnodreamer.petconnect.produto.domain.Produto;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Pedido de compra. Itens e total são fixados na criação (DA-05): não há edição. */
@Entity
@Table(name = "compra")
public class Compra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fornecedor_id")
    private Fornecedor fornecedor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "funcionario_id")
    private Funcionario funcionario;

    @Column(name = "data_compra")
    private LocalDate dataCompra;

    @Column(name = "data_entrega")
    private LocalDate dataEntrega;

    @Column(name = "valor_total")
    private BigDecimal valorTotal = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    private StatusCompra status = StatusCompra.PEDIDO;

    @Column(name = "forma_pagamento")
    private String formaPagamento;

    private String observacoes;

    @OneToMany(mappedBy = "compra", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ItemCompra> itens = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    protected Compra() {
    }

    public Compra(Fornecedor fornecedor, Funcionario funcionario, LocalDate dataCompra, String formaPagamento,
            String observacoes) {
        this.fornecedor = fornecedor;
        this.funcionario = funcionario;
        this.dataCompra = dataCompra;
        this.formaPagamento = formaPagamento;
        this.observacoes = observacoes;
    }

    /** Adiciona item e recalcula o total (soma dos subtotais) — sempre no backend. */
    public void adicionarItem(Produto produto, int quantidade, BigDecimal precoCustoUnitario) {
        itens.add(new ItemCompra(this, produto, quantidade, precoCustoUnitario));
        valorTotal = itens.stream().map(ItemCompra::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void receber(LocalDate dataEntrega) {
        status.exigirTransicaoPara(StatusCompra.RECEBIDO);
        this.status = StatusCompra.RECEBIDO;
        this.dataEntrega = dataEntrega;
    }

    public void cancelar(String motivo) {
        status.exigirTransicaoPara(StatusCompra.CANCELADO);
        this.status = StatusCompra.CANCELADO;
        String nota = "Cancelamento: " + motivo;
        this.observacoes = observacoes == null || observacoes.isBlank() ? nota : observacoes + "\n" + nota;
    }

    public Long getId() {
        return id;
    }

    public Fornecedor getFornecedor() {
        return fornecedor;
    }

    public Funcionario getFuncionario() {
        return funcionario;
    }

    public LocalDate getDataCompra() {
        return dataCompra;
    }

    public LocalDate getDataEntrega() {
        return dataEntrega;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public StatusCompra getStatus() {
        return status;
    }

    public String getFormaPagamento() {
        return formaPagamento;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public List<ItemCompra> getItens() {
        return List.copyOf(itens);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
