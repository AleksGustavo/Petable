package br.com.tecnodreamer.petconnect.financeiro.domain;

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

import java.math.BigDecimal;
import java.time.Instant;

/** Sangria ou suprimento (DA-15). Imutável; o dinheiro correspondente vira lançamento OUTROS. */
@Entity
@Immutable
@Table(name = "caixa_movimento")
public class CaixaMovimento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caixa_id")
    private Caixa caixa;

    @Enumerated(EnumType.STRING)
    private TipoMovimentoCaixa tipo;

    private BigDecimal valor;

    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    protected CaixaMovimento() {
    }

    public CaixaMovimento(Caixa caixa, TipoMovimentoCaixa tipo, BigDecimal valor, String motivo, Usuario usuario) {
        this.caixa = caixa;
        this.tipo = tipo;
        this.valor = valor;
        this.motivo = motivo;
        this.usuario = usuario;
    }

    public Long getId() {
        return id;
    }

    public Caixa getCaixa() {
        return caixa;
    }

    public TipoMovimentoCaixa getTipo() {
        return tipo;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public String getMotivo() {
        return motivo;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
