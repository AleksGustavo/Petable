package br.com.tecnodreamer.petconnect.pet.web;

import jakarta.validation.constraints.NotNull;

public record VinculoProdutoRequest(@NotNull(message = "Selecione o produto") Long produtoId) {
}
