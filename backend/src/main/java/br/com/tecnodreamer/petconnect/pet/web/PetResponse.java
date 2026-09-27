package br.com.tecnodreamer.petconnect.pet.web;

import br.com.tecnodreamer.petconnect.cliente.domain.Cliente;
import br.com.tecnodreamer.petconnect.pet.domain.Idade;
import br.com.tecnodreamer.petconnect.pet.domain.Pet;
import br.com.tecnodreamer.petconnect.pet.domain.Porte;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PetResponse(Long id, String nome, Tutor tutor, CatalogoItem especie, CatalogoItem raca,
        LocalDate dataNascimento, Idade idade, BigDecimal peso, String cor, Porte porte, boolean castrado,
        String fotoUrl, String observacoes, Instant createdAt, Instant updatedAt) {

    public record Tutor(Long id, String nome, String telefone) {
    }

    public static PetResponse of(Pet p, LocalDate hoje) {
        Cliente c = p.getCliente();
        return new PetResponse(p.getId(), p.getNome(), new Tutor(c.getId(), c.getNome(), c.getTelefone()),
                new CatalogoItem(p.getEspecie().getId(), p.getEspecie().getNome()),
                p.getRaca() == null ? null : new CatalogoItem(p.getRaca().getId(), p.getRaca().getNome()),
                p.getDataNascimento(), Idade.calcular(p.getDataNascimento(), hoje), p.getPeso(), p.getCor(),
                p.getPorte(), p.isCastrado(),
                p.getFotoUrl() == null ? null : "/api/v1/pets/" + p.getId() + "/foto",
                p.getObservacoes(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
