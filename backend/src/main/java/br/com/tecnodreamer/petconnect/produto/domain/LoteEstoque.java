package br.com.tecnodreamer.petconnect.produto.domain;

import br.com.tecnodreamer.petconnect.compra.domain.Compra;
import br.com.tecnodreamer.petconnect.fornecedor.domain.Fornecedor;
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
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Lote físico de um produto controlado (D1). Identidade = produto + código do fabricante (D3).
 * Código, validade, custo e origem nascem com o lote e nunca mudam: o histórico do lote é o
 * livro-razão ({@link MovimentacaoEstoque}), não esta linha. O saldo é materializado e só muda
 * por {@link #movimentar(int)}, chamado pelo serviço de estoque com o produto travado (D15).
 */
@Entity
@Table(name = "lote_estoque")
public class LoteEstoque {

    public static final int TAMANHO_MAXIMO_CODIGO = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", updatable = false)
    private Produto produto;

    @Column(name = "codigo_lote", updatable = false)
    private String codigoLote;

    @Column(updatable = false)
    private LocalDate validade;

    private int saldo;

    @Column(name = "custo_unitario", updatable = false)
    private BigDecimal custoUnitario;

    @Enumerated(EnumType.STRING)
    private StatusLote status = StatusLote.DISPONIVEL;

    @Column(name = "data_recebimento", updatable = false)
    private LocalDate dataRecebimento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compra_id", updatable = false)
    private Compra compra;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fornecedor_id", updatable = false)
    private Fornecedor fornecedor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", updatable = false)
    private Usuario usuario;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected LoteEstoque() {
    }

    /** Nasce DISPONIVEL e com saldo zero: o saldo entra por movimentação, nunca pelo construtor. */
    public LoteEstoque(Produto produto, String codigoLote, LocalDate validade, BigDecimal custoUnitario,
            LocalDate dataRecebimento, Compra compra, Fornecedor fornecedor, Usuario usuario) {
        this.produto = produto;
        this.codigoLote = normalizarCodigo(codigoLote);
        this.validade = validade;
        this.custoUnitario = custoUnitario;
        this.dataRecebimento = dataRecebimento;
        this.compra = compra;
        this.fornecedor = fornecedor;
        this.usuario = usuario;
        this.saldo = 0;
    }

    /** Remove espaços das pontas. Maiúsculas/minúsculas já são iguais para o índice único do MySQL. */
    public static String normalizarCodigo(String codigo) {
        String c = codigo == null ? "" : codigo.trim();
        if (c.isEmpty() || c.length() > TAMANHO_MAXIMO_CODIGO) {
            throw new IllegalArgumentException("O código do lote deve ter entre 1 e " + TAMANHO_MAXIMO_CODIGO + " caracteres.");
        }
        return c;
    }

    /** Aplica um delta ao saldo do lote e devolve o saldo anterior. Nunca deixa negativo (D10). */
    public int movimentar(int delta) {
        int anterior = saldo;
        if (anterior + delta < 0) {
            throw new IllegalStateException("Saldo do lote negativo não é permitido");
        }
        saldo = anterior + delta;
        return anterior;
    }

    public void bloquear() {
        this.status = StatusLote.BLOQUEADO;
    }

    public void desbloquear() {
        this.status = StatusLote.DISPONIVEL;
    }

    /** Vencido a partir do dia seguinte à validade (D4). Lote sem validade nunca vence. */
    public boolean isVencidoEm(LocalDate hoje) {
        return validade != null && hoje.isAfter(validade);
    }

    /**
     * Pode entrar no FEFO hoje (D4/D6): disponível, com saldo e fora da margem de bloqueio.
     * Com margem 0 é vendável até a data de validade, inclusive.
     */
    public boolean isVendavelEm(LocalDate hoje, int margemDias) {
        if (status != StatusLote.DISPONIVEL || saldo <= 0) {
            return false;
        }
        return validade == null || !hoje.plusDays(margemDias).isAfter(validade);
    }

    public Long getId() {
        return id;
    }

    public Produto getProduto() {
        return produto;
    }

    public String getCodigoLote() {
        return codigoLote;
    }

    public LocalDate getValidade() {
        return validade;
    }

    public int getSaldo() {
        return saldo;
    }

    public BigDecimal getCustoUnitario() {
        return custoUnitario;
    }

    public StatusLote getStatus() {
        return status;
    }

    public LocalDate getDataRecebimento() {
        return dataRecebimento;
    }

    public Compra getCompra() {
        return compra;
    }

    public Fornecedor getFornecedor() {
        return fornecedor;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
