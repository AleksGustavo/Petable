package br.com.tecnodreamer.petconnect.funcionario.domain;

import br.com.tecnodreamer.petconnect.endereco.domain.Endereco;
import br.com.tecnodreamer.petconnect.usuario.domain.Usuario;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.time.LocalDate;

/**
 * Perfil operacional opcional de um usuário (1:1, DA-01). Não tem "ativo"
 * próprio (DA-02): está disponível enquanto a conta do usuário estiver ativa.
 */
@Entity
@Table(name = "funcionario")
public class Funcionario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", updatable = false)
    private Usuario usuario;

    private String nome;
    private String cpf;

    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;

    @Enumerated(EnumType.STRING)
    private Sexo sexo = Sexo.OUTRO;

    private String telefone;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "endereco_id")
    private Endereco endereco;

    private String observacoes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Funcionario() {
    }

    public Funcionario(Usuario usuario, String cpf) {
        this.usuario = usuario;
        this.cpf = cpf;
    }

    /** Dados que o próprio funcionário pode editar no perfil (sem CPF). */
    public void atualizarDados(String nome, LocalDate dataNascimento, Sexo sexo, String telefone, String observacoes) {
        this.nome = nome;
        this.dataNascimento = dataNascimento;
        this.sexo = sexo == null ? Sexo.OUTRO : sexo;
        this.telefone = telefone;
        this.observacoes = observacoes;
    }

    /** Correção de CPF: só pelo ADMIN. */
    public void corrigirCpf(String cpf) {
        this.cpf = cpf;
    }

    public void definirEndereco(Endereco endereco) {
        this.endereco = endereco;
    }

    /** Disponível para novos agendamentos quando a conta está ativa. */
    public boolean isDisponivel() {
        return usuario.isAtivo();
    }

    public Long getId() {
        return id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public String getNome() {
        return nome;
    }

    public String getCpf() {
        return cpf;
    }

    public LocalDate getDataNascimento() {
        return dataNascimento;
    }

    public Sexo getSexo() {
        return sexo;
    }

    public String getTelefone() {
        return telefone;
    }

    public Endereco getEndereco() {
        return endereco;
    }

    public String getObservacoes() {
        return observacoes;
    }
}
