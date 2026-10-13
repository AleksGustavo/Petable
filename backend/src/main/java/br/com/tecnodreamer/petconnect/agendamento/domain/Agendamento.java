package br.com.tecnodreamer.petconnect.agendamento.domain;

import br.com.tecnodreamer.petconnect.funcionario.domain.Funcionario;
import br.com.tecnodreamer.petconnect.pet.domain.Pet;
import br.com.tecnodreamer.petconnect.servico.domain.Servico;
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
import java.time.LocalDateTime;

@Entity
@Table(name = "agendamento")
public class Agendamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pet_id")
    private Pet pet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "servico_id")
    private Servico servico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "funcionario_id")
    private Funcionario funcionario;

    @Column(name = "data_hora")
    private LocalDateTime dataHora;

    @Enumerated(EnumType.STRING)
    private StatusAgendamento status = StatusAgendamento.AGENDADO;

    /** Snapshot do preço no momento do agendamento; mudanças no catálogo não o alteram. */
    @Column(name = "total_estimado")
    private BigDecimal totalEstimado;

    private String observacoes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Agendamento() {
    }

    public Agendamento(Pet pet, Servico servico, Funcionario funcionario, LocalDateTime dataHora,
            BigDecimal totalEstimado, String observacoes) {
        reagendar(pet, servico, funcionario, dataHora, totalEstimado, observacoes);
    }

    /** Troca dados editáveis (o serviço de aplicação garante que o status permite). */
    public final void reagendar(Pet pet, Servico servico, Funcionario funcionario, LocalDateTime dataHora,
            BigDecimal totalEstimado, String observacoes) {
        this.pet = pet;
        this.servico = servico;
        this.funcionario = funcionario;
        this.dataHora = dataHora;
        this.totalEstimado = totalEstimado;
        this.observacoes = observacoes;
    }

    public void mudarStatus(StatusAgendamento novo) {
        status.exigirTransicaoPara(novo);
        this.status = novo;
    }

    /** Acrescenta uma nota identificável (ex.: motivo do cancelamento) sem apagar a observação anterior. */
    public void anexarObservacao(String marcador, String texto) {
        String nota = "[" + marcador + "] " + texto;
        this.observacoes = observacoes == null || observacoes.isBlank() ? nota : observacoes + "\n" + nota;
    }

    /** Fim previsto (intervalo semiaberto [início, fim)), pela duração atual do serviço. */
    public LocalDateTime fimPrevisto() {
        Integer duracao = servico.getDuracaoMinutos();
        return duracao == null ? dataHora : dataHora.plusMinutes(duracao);
    }

    public boolean sobrepoe(LocalDateTime inicio, LocalDateTime fim) {
        return dataHora.isBefore(fim) && fimPrevisto().isAfter(inicio);
    }

    public Long getId() {
        return id;
    }

    public Pet getPet() {
        return pet;
    }

    public Servico getServico() {
        return servico;
    }

    public Funcionario getFuncionario() {
        return funcionario;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public StatusAgendamento getStatus() {
        return status;
    }

    public BigDecimal getTotalEstimado() {
        return totalEstimado;
    }

    public String getObservacoes() {
        return observacoes;
    }
}
