package br.com.tecnodreamer.petconnect.usuario.domain;

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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    private String email;

    @Column(name = "senha_hash")
    private String senhaHash;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "papel_id")
    private Papel papel;

    private boolean ativo = true;

    /** Versão da credencial: sobe a cada troca de senha, papel ou ativação; JWTs antigos deixam de valer. */
    @Column(name = "token_version")
    private int tokenVersion;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Usuario() {
    }

    public Usuario(String username, String email, String senhaHash, Papel papel) {
        this.username = username;
        this.email = email;
        this.senhaHash = senhaHash;
        this.papel = papel;
    }

    public void alterarConta(String username, String email) {
        this.username = username;
        this.email = email;
    }

    /** Devolve se o papel mudou (só então a versão da credencial sobe). */
    public boolean alterarPapel(Papel novo) {
        if (papel != null && papel.getNome().equals(novo.getNome())) {
            return false;
        }
        this.papel = novo;
        this.tokenVersion++;
        return true;
    }

    public void desativar() {
        this.ativo = false;
        this.tokenVersion++;
    }

    public void ativar() {
        this.ativo = true;
        this.tokenVersion++;
    }

    public void definirSenha(String senhaHash) {
        this.senhaHash = senhaHash;
        this.tokenVersion++;
    }

    public boolean isAdmin() {
        return Papel.ADMIN.equals(papel.getNome());
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public Papel getPapel() {
        return papel;
    }

    public boolean isAtivo() {
        return ativo;
    }
}
