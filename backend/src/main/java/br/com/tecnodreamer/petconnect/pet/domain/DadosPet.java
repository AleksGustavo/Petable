package br.com.tecnodreamer.petconnect.pet.domain;

import br.com.tecnodreamer.petconnect.cliente.domain.Cliente;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Dados editáveis de um pet, já validados e resolvidos pelo serviço. */
public record DadosPet(Cliente tutor, String nome, Especie especie, Raca raca, LocalDate dataNascimento,
        BigDecimal peso, String cor, Porte porte, boolean castrado, String observacoes) {
}
