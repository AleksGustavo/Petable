package br.com.tecnodreamer.petconnect.pet.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "raca")
public class Raca {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "especie_id")
    private Especie especie;

    private String nome;

    protected Raca() {
    }

    public boolean pertenceA(Especie outra) {
        return especie.getId().equals(outra.getId());
    }

    public Long getId() {
        return id;
    }

    public Especie getEspecie() {
        return especie;
    }

    public String getNome() {
        return nome;
    }
}
