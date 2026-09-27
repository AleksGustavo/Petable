package br.com.tecnodreamer.petconnect.pet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "historico_consulta")
public class HistoricoConsulta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pet_id")
    private Pet pet;

    @Column(name = "data_consulta")
    private LocalDateTime dataConsulta;

    private String veterinario;
    private String motivo;
    private String diagnostico;
    private String tratamento;
    private String observacoes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    protected HistoricoConsulta() {
    }

    public HistoricoConsulta(Pet pet, LocalDateTime dataConsulta, String veterinario, String motivo,
            String diagnostico, String tratamento, String observacoes) {
        this.pet = pet;
        this.dataConsulta = dataConsulta;
        this.veterinario = veterinario;
        this.motivo = motivo;
        this.diagnostico = diagnostico;
        this.tratamento = tratamento;
        this.observacoes = observacoes;
    }

    public Long getId() {
        return id;
    }

    public Pet getPet() {
        return pet;
    }

    public LocalDateTime getDataConsulta() {
        return dataConsulta;
    }

    public String getVeterinario() {
        return veterinario;
    }

    public String getMotivo() {
        return motivo;
    }

    public String getDiagnostico() {
        return diagnostico;
    }

    public String getTratamento() {
        return tratamento;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
