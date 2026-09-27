package br.com.tecnodreamer.petconnect.financeiro.domain;

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
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Lançamento financeiro (tabela {@code movimentacao_financeira}). Imutável: nunca é
 * editado nem apagado; correção é sempre um novo lançamento com {@code estorno_de_id}
 * (DA-07, DA-13). Só o {@code FinanceiroService} cria lançamentos (DA-01).
 */
@Entity
@Immutable
@Table(name = "movimentacao_financeira")
public class Lancamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private TipoLancamento tipo;

    @Enumerated(EnumType.STRING)
    private CategoriaLancamento categoria;

    private String descricao;

    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento")
    private FormaPagamento formaPagamento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caixa_id")
    private Caixa caixa;

    @Column(name = "data_movimentacao")
    private LocalDate dataMovimentacao;

    @Column(name = "venda_id")
    private Long vendaId;

    @Column(name = "compra_id")
    private Long compraId;

    @Column(name = "servico_realizado_id")
    private Long servicoRealizadoId;

    @Column(name = "conta_pagar_id")
    private Long contaPagarId;

    @Column(name = "estorno_de_id")
    private Long estornoDeId;

    @Column(name = "chave_origem")
    private String chaveOrigem;

    @Column(name = "motivo_estorno")
    private String motivoEstorno;

    private String observacoes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    protected Lancamento() {
    }

    /** Origem do lançamento (no máximo uma entre venda, compra e serviço — CHECK da V1). */
    public record Origem(Long vendaId, Long compraId, Long servicoRealizadoId, Long contaPagarId) {
        public static final Origem NENHUMA = new Origem(null, null, null, null);

        public static Origem venda(Long id) {
            return new Origem(id, null, null, null);
        }

        public static Origem servico(Long id) {
            return new Origem(null, null, id, null);
        }

        public static Origem conta(Long compraId, Long contaId) {
            return new Origem(null, compraId, null, contaId);
        }
    }

    public Lancamento(TipoLancamento tipo, CategoriaLancamento categoria, String descricao, BigDecimal valor,
            FormaPagamento formaPagamento, LocalDate data, Usuario usuario, Caixa caixa, Origem origem,
            String chaveOrigem, Long estornoDeId, String motivoEstorno, String observacoes) {
        this.tipo = tipo;
        this.categoria = categoria;
        this.descricao = descricao;
        this.valor = valor;
        this.formaPagamento = formaPagamento;
        this.dataMovimentacao = data;
        this.usuario = usuario;
        this.caixa = caixa;
        this.vendaId = origem.vendaId();
        this.compraId = origem.compraId();
        this.servicoRealizadoId = origem.servicoRealizadoId();
        this.contaPagarId = origem.contaPagarId();
        this.chaveOrigem = chaveOrigem;
        this.estornoDeId = estornoDeId;
        this.motivoEstorno = motivoEstorno;
        this.observacoes = observacoes;
    }

    /** Lançamento manual (chave MANUAL:uuid) e ainda não é, ele mesmo, um estorno. */
    public boolean isManual() {
        return chaveOrigem != null && chaveOrigem.startsWith("MANUAL:");
    }

    public Long getId() {
        return id;
    }

    public TipoLancamento getTipo() {
        return tipo;
    }

    public CategoriaLancamento getCategoria() {
        return categoria;
    }

    public String getDescricao() {
        return descricao;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public FormaPagamento getFormaPagamento() {
        return formaPagamento;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public Caixa getCaixa() {
        return caixa;
    }

    public LocalDate getDataMovimentacao() {
        return dataMovimentacao;
    }

    public Long getVendaId() {
        return vendaId;
    }

    public Long getCompraId() {
        return compraId;
    }

    public Long getServicoRealizadoId() {
        return servicoRealizadoId;
    }

    public Long getContaPagarId() {
        return contaPagarId;
    }

    public Long getEstornoDeId() {
        return estornoDeId;
    }

    public String getChaveOrigem() {
        return chaveOrigem;
    }

    public String getMotivoEstorno() {
        return motivoEstorno;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
