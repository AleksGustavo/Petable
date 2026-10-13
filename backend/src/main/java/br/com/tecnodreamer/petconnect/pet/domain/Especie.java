package br.com.tecnodreamer.petconnect.pet.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Catálogo somente leitura nesta etapa (dados vêm da V2). */
@Entity
@Table(name = "especie")
public class Especie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;

    protected Especie() {
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }
}
