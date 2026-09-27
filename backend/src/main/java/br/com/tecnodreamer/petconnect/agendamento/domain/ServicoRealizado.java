package br.com.tecnodreamer.petconnect.agendamento.domain;

import br.com.tecnodreamer.petconnect.funcionario.domain.Funcionario;
import br.com.tecnodreamer.petconnect.pet.domain.Pet;
import br.com.tecnodreamer.petconnect.servico.domain.Servico;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/** Registro operacional do serviço executado. Não é venda nem recebimento (financeiro fica para outra etapa). */
@Entity
@Table(name = "servico_realizado")
public class ServicoRealizado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agendamento_id")
    private Agendamento agendamento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pet_id")
    private Pet pet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "servico_id")
    private Servico servico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "funcionario_id")
    private Funcionario funcionario;

    @Column(name = "data_servico")
    private LocalDateTime dataServico;

    private BigDecimal valor;
    private String observacoes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    protected ServicoRealizado() {
    }

    public ServicoRealizado(Agendamento agendamento, LocalDateTime dataServico, BigDecimal valor, String observacoes) {
        this.agendamento = agendamento;
        this.pet = agendamento.getPet();
        this.servico = agendamento.getServico();
        this.funcionario = agendamento.getFuncionario();
        this.dataServico = dataServico;
        this.valor = valor;
        this.observacoes = observacoes;
    }

    public Long getId() {
        return id;
    }

    public Agendamento getAgendamento() {
        return agendamento;
    }

    public Servico getServico() {
        return servico;
    }

    public LocalDateTime getDataServico() {
        return dataServico;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public String getObservacoes() {
        return observacoes;
    }
}
