package br.com.tecnodreamer.petconnect.fornecedor.domain;

import br.com.tecnodreamer.petconnect.endereco.domain.Endereco;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "fornecedor")
public class Fornecedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome_fantasia")
    private String nomeFantasia;

    @Column(name = "razao_social")
    private String razaoSocial;

    private String cnpj;
    private String telefone;
    private String email;
    private String contato;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "endereco_id")
    private Endereco endereco;

    private String observacoes;

    private boolean ativo = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Fornecedor() {
    }

    public Fornecedor(String nomeFantasia, String razaoSocial, String cnpj, String telefone, String email, String contato,
            String observacoes) {
        atualizar(nomeFantasia, razaoSocial, cnpj, telefone, email, contato, observacoes);
    }

    public final void atualizar(String nomeFantasia, String razaoSocial, String cnpj, String telefone, String email,
            String contato, String observacoes) {
        this.nomeFantasia = nomeFantasia;
        this.razaoSocial = razaoSocial;
        this.cnpj = cnpj;
        this.telefone = telefone;
        this.email = email;
        this.contato = contato;
        this.observacoes = observacoes;
    }

    public void definirEndereco(Endereco endereco) {
        this.endereco = endereco;
    }

    public void desativar() {
        this.ativo = false;
    }

    public void ativar() {
        this.ativo = true;
    }

    public Long getId() {
        return id;
    }

    public String getNomeFantasia() {
        return nomeFantasia;
    }

    public String getRazaoSocial() {
        return razaoSocial;
    }

    public String getCnpj() {
        return cnpj;
    }

    public String getTelefone() {
        return telefone;
    }

    public String getEmail() {
        return email;
    }

    public String getContato() {
        return contato;
    }

    public Endereco getEndereco() {
        return endereco;
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
