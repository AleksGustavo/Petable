package br.com.tecnodreamer.petconnect.financeiro.domain;

import br.com.tecnodreamer.petconnect.compra.domain.Compra;
import br.com.tecnodreamer.petconnect.shared.error.ConflictException;
import br.com.tecnodreamer.petconnect.usuario.domain.Usuario;
import br.com.tecnodreamer.petconnect.venda.domain.FormaPagamento;
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
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Conta a pagar gerada no recebimento da compra (DA-09). Pagamento integral, uma única vez (DA-11). */
@Entity
@Table(name = "conta_pagar")
public class ContaPagar {

    public static final String JA_PAGA = "Esta conta já foi paga.";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compra_id")
    private Compra compra;

    private String descricao;

    @Column(name = "valor_original")
    private BigDecimal valorOriginal;

    private BigDecimal saldo;

    @Column(name = "data_vencimento")
    private LocalDate dataVencimento;

    @Enumerated(EnumType.STRING)
    private StatusConta status = StatusConta.ABERTA;

    @Column(name = "paga_em")
    private LocalDateTime pagaEm;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento")
    private FormaPagamento formaPagamento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_pagamento_id")
    private Usuario usuarioPagamento;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    protected ContaPagar() {
    }

    public ContaPagar(Compra compra, String descricao, BigDecimal valor, LocalDate dataVencimento) {
        this.compra = compra;
        this.descricao = descricao;
        this.valorOriginal = valor;
        this.saldo = valor;
        this.dataVencimento = dataVencimento;
    }

    public void pagar(FormaPagamento forma, Usuario usuario, LocalDateTime quando) {
        if (status == StatusConta.PAGA) {
            throw new ConflictException(JA_PAGA);
        }
        this.status = StatusConta.PAGA;
        this.saldo = BigDecimal.ZERO.setScale(2);
        this.formaPagamento = forma;
        this.usuarioPagamento = usuario;
        this.pagaEm = quando;
    }

    public Long getId() {
        return id;
    }

    public Compra getCompra() {
        return compra;
    }

    public String getDescricao() {
        return descricao;
    }

    public BigDecimal getValorOriginal() {
        return valorOriginal;
    }

    public BigDecimal getSaldo() {
        return saldo;
    }

    public LocalDate getDataVencimento() {
        return dataVencimento;
    }

    public StatusConta getStatus() {
        return status;
    }

    public LocalDateTime getPagaEm() {
        return pagaEm;
    }

    public FormaPagamento getFormaPagamento() {
        return formaPagamento;
    }

    public Usuario getUsuarioPagamento() {
        return usuarioPagamento;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
