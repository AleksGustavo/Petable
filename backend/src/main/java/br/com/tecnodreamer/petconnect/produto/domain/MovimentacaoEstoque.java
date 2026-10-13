package br.com.tecnodreamer.petconnect.produto.domain;

import br.com.tecnodreamer.petconnect.compra.domain.Compra;
import br.com.tecnodreamer.petconnect.usuario.domain.Usuario;
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

import java.time.Instant;

/** Trilha imutável de cada mudança de saldo (DA-01): nunca é editada nem apagada. */
@Entity
@Immutable
@Table(name = "movimentacao_estoque")
public class MovimentacaoEstoque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id")
    private Produto produto;

    @Enumerated(EnumType.STRING)
    private TipoMovimentacao tipo;

    private int quantidade;

    @Column(name = "saldo_anterior")
    private int saldoAnterior;

    @Column(name = "saldo_resultante")
    private int saldoResultante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compra_id")
    private Compra compra;

    @Column(name = "venda_id")
    private Long vendaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    private String motivo;

    /** Lote movimentado (nulo = produto sem controle de lote, como todo o histórico anterior). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_id")
    private LoteEstoque lote;

    @Column(name = "lote_saldo_anterior")
    private Integer loteSaldoAnterior;

    @Column(name = "lote_saldo_resultante")
    private Integer loteSaldoResultante;

    /** Agrupa os movimentos de uma mesma operação (ex.: o par ESTORNO_VENDA + PERDA do descarte). */
    @Column(name = "operacao_id")
    private String operacaoId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    protected MovimentacaoEstoque() {
    }

    public MovimentacaoEstoque(Produto produto, TipoMovimentacao tipo, int quantidade, int saldoAnterior,
            int saldoResultante, Compra compra, Usuario usuario, String motivo) {
        this.produto = produto;
        this.tipo = tipo;
        this.quantidade = quantidade;
        this.saldoAnterior = saldoAnterior;
        this.saldoResultante = saldoResultante;
        this.compra = compra;
        this.usuario = usuario;
        this.motivo = motivo;
    }

    /** Saída ou estorno de venda: guarda só o id da venda (o módulo de estoque não depende da entidade). */
    public MovimentacaoEstoque(Produto produto, TipoMovimentacao tipo, int quantidade, int saldoAnterior,
            int saldoResultante, Long vendaId, Usuario usuario) {
        this(produto, tipo, quantidade, saldoAnterior, saldoResultante, null, usuario, null);
        this.vendaId = vendaId;
    }

    /**
     * Movimento de um lote: guarda o saldo do lote antes e depois (I6). {@code compra} ou {@code vendaId}
     * identificam a origem quando houver; {@code operacaoId} liga movimentos da mesma operação.
     */
    public MovimentacaoEstoque(Produto produto, TipoMovimentacao tipo, int quantidade, int saldoAnterior,
            int saldoResultante, Compra compra, Long vendaId, Usuario usuario, String motivo, LoteEstoque lote,
            int loteSaldoAnterior, String operacaoId) {
        this(produto, tipo, quantidade, saldoAnterior, saldoResultante, compra, usuario, motivo);
        this.vendaId = vendaId;
        this.lote = lote;
        this.loteSaldoAnterior = loteSaldoAnterior;
        this.loteSaldoResultante = loteSaldoAnterior + quantidade;
        this.operacaoId = operacaoId;
    }

    public Long getId() {
        return id;
    }

    public Produto getProduto() {
        return produto;
    }

    public TipoMovimentacao getTipo() {
        return tipo;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public int getSaldoAnterior() {
        return saldoAnterior;
    }

    public int getSaldoResultante() {
        return saldoResultante;
    }

    public Compra getCompra() {
        return compra;
    }

    public Long getVendaId() {
        return vendaId;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public String getMotivo() {
        return motivo;
    }

    public LoteEstoque getLote() {
        return lote;
    }

    public Integer getLoteSaldoAnterior() {
        return loteSaldoAnterior;
    }

    public Integer getLoteSaldoResultante() {
        return loteSaldoResultante;
    }

    public String getOperacaoId() {
        return operacaoId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
