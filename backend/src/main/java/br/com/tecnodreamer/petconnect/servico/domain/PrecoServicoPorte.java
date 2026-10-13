package br.com.tecnodreamer.petconnect.servico.domain;

import br.com.tecnodreamer.petconnect.pet.domain.Porte;
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

@Entity
@Table(name = "preco_servico_porte")
public class PrecoServicoPorte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "servico_id")
    private Servico servico;

    @Enumerated(EnumType.STRING)
    private Porte porte;

    private BigDecimal preco;

    protected PrecoServicoPorte() {
    }

    PrecoServicoPorte(Servico servico, Porte porte, BigDecimal preco) {
        this.servico = servico;
        this.porte = porte;
        this.preco = preco;
    }

    void alterarPreco(BigDecimal preco) {
        this.preco = preco;
    }

    public Porte getPorte() {
        return porte;
    }

    public BigDecimal getPreco() {
        return preco;
    }
}
