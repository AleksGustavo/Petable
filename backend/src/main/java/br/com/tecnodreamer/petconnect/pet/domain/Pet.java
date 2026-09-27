package br.com.tecnodreamer.petconnect.pet.domain;

import br.com.tecnodreamer.petconnect.cliente.domain.Cliente;
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

@Entity
@Table(name = "pet")
public class Pet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    private String nome;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "especie_id")
    private Especie especie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raca_id")
    private Raca raca;

    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;

    private BigDecimal peso;
    private String cor;

    @Enumerated(EnumType.STRING)
    private Porte porte;

    private boolean castrado;

    /** Chave interna do arquivo no storage (nunca um caminho físico nem URL pública). */
    @Column(name = "foto_url")
    private String fotoUrl;

    private String observacoes;

    private boolean ativo = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Pet() {
    }

    public Pet(DadosPet dados) {
        atualizar(dados);
    }

    public final void atualizar(DadosPet d) {
        this.cliente = d.tutor();
        this.nome = d.nome();
        this.especie = d.especie();
        this.raca = d.raca();
        this.dataNascimento = d.dataNascimento();
        this.peso = d.peso();
        this.cor = d.cor();
        this.porte = d.porte();
        this.castrado = d.castrado();
        this.observacoes = d.observacoes();
    }

    public void desativar() {
        this.ativo = false;
    }

    /** Troca a foto e devolve a chave anterior (para o serviço apagar o arquivo antigo). */
    public String definirFoto(String chave) {
        String anterior = this.fotoUrl;
        this.fotoUrl = chave;
        return anterior;
    }

    public Long getId() {
        return id;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public String getNome() {
        return nome;
    }

    public Especie getEspecie() {
        return especie;
    }

    public Raca getRaca() {
        return raca;
    }

    public LocalDate getDataNascimento() {
        return dataNascimento;
    }

    public BigDecimal getPeso() {
        return peso;
    }

    public String getCor() {
        return cor;
    }

    public Porte getPorte() {
        return porte;
    }

    public boolean isCastrado() {
        return castrado;
    }

    public String getFotoUrl() {
        return fotoUrl;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
