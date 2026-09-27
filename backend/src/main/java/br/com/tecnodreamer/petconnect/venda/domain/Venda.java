package br.com.tecnodreamer.petconnect.venda.domain;

import br.com.tecnodreamer.petconnect.cliente.domain.Cliente;
import br.com.tecnodreamer.petconnect.financeiro.domain.Caixa;
import br.com.tecnodreamer.petconnect.funcionario.domain.Funcionario;
import br.com.tecnodreamer.petconnect.produto.domain.Produto;
import br.com.tecnodreamer.petconnect.shared.error.ConflictException;
import br.com.tecnodreamer.petconnect.usuario.domain.Usuario;
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
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Venda de balcão. Nasce CONCLUIDA numa transação única; itens, preços e totais
 * não são editados depois. O único evento posterior é o cancelamento (DA-07).
 * Datas em horário local de São Paulo, como a agenda.
 */
@Entity
@Table(name = "venda")
public class Venda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "funcionario_id")
    private Funcionario funcionario;

    /** Operador (usuário logado). Nulo só em dados migrados do legado. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    /** Caixa aberto do operador em que a venda entrou (DA-03). Nulo só em vendas anteriores à V8. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caixa_id")
    private Caixa caixa;

    @Column(name = "data_venda")
    private LocalDateTime dataVenda;

    @Column(name = "valor_total")
    private BigDecimal valorTotal = BigDecimal.ZERO;

    private BigDecimal desconto = BigDecimal.ZERO;

    @Column(name = "valor_recebido")
    private BigDecimal valorRecebido;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento")
    private FormaPagamento formaPagamento;

    @Enumerated(EnumType.STRING)
    private StatusVenda status = StatusVenda.CONCLUIDA;

    private String observacoes;

    @Column(name = "chave_idempotencia")
    private String chaveIdempotencia;

    @Column(name = "cancelada_em")
    private LocalDateTime canceladaEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelada_por")
    private Usuario canceladaPor;

    @Column(name = "motivo_cancelamento")
    private String motivoCancelamento;

    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ItemVenda> itens = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    protected Venda() {
    }

    public Venda(Cliente cliente, Funcionario funcionario, Usuario operador, LocalDateTime dataVenda,
            FormaPagamento formaPagamento, String observacoes, String chaveIdempotencia) {
        this.cliente = cliente;
        this.funcionario = funcionario;
        this.usuario = operador;
        this.dataVenda = dataVenda;
        this.formaPagamento = formaPagamento;
        this.observacoes = observacoes;
        this.chaveIdempotencia = chaveIdempotencia;
    }

    public void registrarNoCaixa(Caixa caixa) {
        this.caixa = caixa;
    }

    /** Preço vem do produto (travado) — nunca do cliente (DA-02). */
    public void adicionarItem(Produto produto, int quantidade) {
        itens.add(new ItemVenda(this, produto, quantidade, produto.getPrecoVenda().setScale(2, RoundingMode.HALF_UP)));
    }

    /** Congela desconto, total e valor recebido. Validações de teto/troco ficam no serviço. */
    public void fechar(BigDecimal desconto, BigDecimal valorRecebido) {
        this.desconto = desconto.setScale(2, RoundingMode.HALF_UP);
        this.valorTotal = subtotal().subtract(this.desconto);
        this.valorRecebido = valorRecebido == null ? null : valorRecebido.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal subtotal() {
        return itens.stream().map(ItemVenda::subtotal).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    /** Troco só existe em dinheiro. */
    public BigDecimal troco() {
        return valorRecebido == null ? null : valorRecebido.subtract(valorTotal);
    }

    public void cancelar(Usuario usuario, String motivo, LocalDateTime quando) {
        if (status == StatusVenda.CANCELADA) {
            throw new ConflictException("A venda já está cancelada.");
        }
        this.status = StatusVenda.CANCELADA;
        this.canceladaPor = usuario;
        this.canceladaEm = quando;
        this.motivoCancelamento = motivo;
    }

    public Long getId() {
        return id;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public Funcionario getFuncionario() {
        return funcionario;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public Caixa getCaixa() {
        return caixa;
    }

    public LocalDateTime getDataVenda() {
        return dataVenda;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public BigDecimal getDesconto() {
        return desconto;
    }

    public BigDecimal getValorRecebido() {
        return valorRecebido;
    }

    public FormaPagamento getFormaPagamento() {
        return formaPagamento;
    }

    public StatusVenda getStatus() {
        return status;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public String getChaveIdempotencia() {
        return chaveIdempotencia;
    }

    public LocalDateTime getCanceladaEm() {
        return canceladaEm;
    }

    public Usuario getCanceladaPor() {
        return canceladaPor;
    }

    public String getMotivoCancelamento() {
        return motivoCancelamento;
    }

    public List<ItemVenda> getItens() {
        return List.copyOf(itens);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
