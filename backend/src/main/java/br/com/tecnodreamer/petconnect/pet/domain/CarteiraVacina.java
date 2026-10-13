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
import java.time.LocalDate;

/** Um lançamento (dose aplicada) na carteira de vacinação do pet. */
@Entity
@Table(name = "carteira_vacina")
public class CarteiraVacina {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pet_id")
    private Pet pet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vacina_id")
    private Vacina vacina;

    @Column(name = "data_aplicacao")
    private LocalDate dataAplicacao;

    @Column(name = "data_proxima")
    private LocalDate dataProxima;

    private String veterinario;
    private String lote;
    private String observacoes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    protected CarteiraVacina() {
    }

    public CarteiraVacina(Pet pet, Vacina vacina, LocalDate dataAplicacao, LocalDate dataProxima,
            String veterinario, String lote, String observacoes) {
        this.pet = pet;
        this.vacina = vacina;
        this.dataAplicacao = dataAplicacao;
        this.dataProxima = dataProxima;
        this.veterinario = veterinario;
        this.lote = lote;
        this.observacoes = observacoes;
    }

    public boolean pertenceAoPet(Long petId) {
        return pet.getId().equals(petId);
    }

    public StatusVacina status(LocalDate hoje) {
        return StatusVacina.calcular(dataProxima, hoje);
    }

    public Long getId() {
        return id;
    }

    public Pet getPet() {
        return pet;
    }

    public Vacina getVacina() {
        return vacina;
    }

    public LocalDate getDataAplicacao() {
        return dataAplicacao;
    }

    public LocalDate getDataProxima() {
        return dataProxima;
    }

    public String getVeterinario() {
        return veterinario;
    }

    public String getLote() {
        return lote;
    }

    public String getObservacoes() {
        return observacoes;
    }
}
