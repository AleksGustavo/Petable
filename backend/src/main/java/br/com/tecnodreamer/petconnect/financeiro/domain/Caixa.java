package br.com.tecnodreamer.petconnect.financeiro.domain;

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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

/**
 * Sessão de caixa de um usuário. No máximo um ABERTO por usuário (UNIQUE sobre a
 * coluna gerada {@code usuario_aberto_id}, DA-04). O fechamento é definitivo (DA-16).
 */
@Entity
@Table(name = "caixa")
public class Caixa {

    public static final String JA_FECHADO = "Este caixa já está fechado.";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    private StatusCaixa status = StatusCaixa.ABERTO;

    @Column(name = "aberto_em")
    private LocalDateTime abertoEm;

    @Column(name = "fundo_abertura")
    private BigDecimal fundoAbertura;

    @Column(name = "fechado_em")
    private LocalDateTime fechadoEm;

    @Column(name = "esperado_dinheiro") private BigDecimal esperadoDinheiro;
    @Column(name = "contado_dinheiro") private BigDecimal contadoDinheiro;
    @Column(name = "diferenca_dinheiro") private BigDecimal diferencaDinheiro;
    @Column(name = "esperado_cartao_credito") private BigDecimal esperadoCartaoCredito;
    @Column(name = "contado_cartao_credito") private BigDecimal contadoCartaoCredito;
    @Column(name = "diferenca_cartao_credito") private BigDecimal diferencaCartaoCredito;
    @Column(name = "esperado_cartao_debito") private BigDecimal esperadoCartaoDebito;
    @Column(name = "contado_cartao_debito") private BigDecimal contadoCartaoDebito;
    @Column(name = "diferenca_cartao_debito") private BigDecimal diferencaCartaoDebito;
    @Column(name = "esperado_pix") private BigDecimal esperadoPix;
    @Column(name = "contado_pix") private BigDecimal contadoPix;
    @Column(name = "diferenca_pix") private BigDecimal diferencaPix;
    @Column(name = "esperado_transferencia") private BigDecimal esperadoTransferencia;
    @Column(name = "contado_transferencia") private BigDecimal contadoTransferencia;
    @Column(name = "diferenca_transferencia") private BigDecimal diferencaTransferencia;

    @Column(name = "diferenca_total")
    private BigDecimal diferencaTotal;

    @Column(name = "motivo_diferenca")
    private String motivoDiferenca;

    protected Caixa() {
    }

    public Caixa(Usuario usuario, BigDecimal fundoAbertura, LocalDateTime abertoEm) {
        this.usuario = usuario;
        this.fundoAbertura = fundoAbertura;
        this.abertoEm = abertoEm;
    }

    public boolean isAberto() {
        return status == StatusCaixa.ABERTO;
    }

    /** Grava esperado/contado/diferença por forma e fecha definitivamente. */
    public void fechar(Map<FormaPagamento, BigDecimal> esperado, Map<FormaPagamento, BigDecimal> contado,
            String motivo, LocalDateTime quando) {
        if (!isAberto()) {
            throw new ConflictException(JA_FECHADO);
        }
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (FormaPagamento f : FormaPagamento.values()) {
            BigDecimal e = esperado.getOrDefault(f, BigDecimal.ZERO.setScale(2));
            BigDecimal c = contado.getOrDefault(f, BigDecimal.ZERO.setScale(2));
            BigDecimal d = c.subtract(e);
            total = total.add(d);
            switch (f) {
                case DINHEIRO -> { esperadoDinheiro = e; contadoDinheiro = c; diferencaDinheiro = d; }
                case CARTAO_CREDITO -> { esperadoCartaoCredito = e; contadoCartaoCredito = c; diferencaCartaoCredito = d; }
                case CARTAO_DEBITO -> { esperadoCartaoDebito = e; contadoCartaoDebito = c; diferencaCartaoDebito = d; }
                case PIX -> { esperadoPix = e; contadoPix = c; diferencaPix = d; }
                case TRANSFERENCIA -> { esperadoTransferencia = e; contadoTransferencia = c; diferencaTransferencia = d; }
            }
        }
        this.diferencaTotal = total;
        this.motivoDiferenca = motivo;
        this.status = StatusCaixa.FECHADO;
        this.fechadoEm = quando;
    }

    public Map<FormaPagamento, BigDecimal> esperado() {
        return porForma(esperadoDinheiro, esperadoCartaoCredito, esperadoCartaoDebito, esperadoPix, esperadoTransferencia);
    }

    public Map<FormaPagamento, BigDecimal> contado() {
        return porForma(contadoDinheiro, contadoCartaoCredito, contadoCartaoDebito, contadoPix, contadoTransferencia);
    }

    public Map<FormaPagamento, BigDecimal> diferenca() {
        return porForma(diferencaDinheiro, diferencaCartaoCredito, diferencaCartaoDebito, diferencaPix, diferencaTransferencia);
    }

    private Map<FormaPagamento, BigDecimal> porForma(BigDecimal dinheiro, BigDecimal credito, BigDecimal debito,
            BigDecimal pix, BigDecimal transferencia) {
        if (isAberto()) {
            return null;
        }
        Map<FormaPagamento, BigDecimal> m = new EnumMap<>(FormaPagamento.class);
        m.put(FormaPagamento.DINHEIRO, dinheiro);
        m.put(FormaPagamento.CARTAO_CREDITO, credito);
        m.put(FormaPagamento.CARTAO_DEBITO, debito);
        m.put(FormaPagamento.PIX, pix);
        m.put(FormaPagamento.TRANSFERENCIA, transferencia);
        return m;
    }

    public Long getId() {
        return id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public StatusCaixa getStatus() {
        return status;
    }

    public LocalDateTime getAbertoEm() {
        return abertoEm;
    }

    public BigDecimal getFundoAbertura() {
        return fundoAbertura;
    }

    public LocalDateTime getFechadoEm() {
        return fechadoEm;
    }

    public BigDecimal getDiferencaTotal() {
        return diferencaTotal;
    }

    public String getMotivoDiferenca() {
        return motivoDiferenca;
    }
}
