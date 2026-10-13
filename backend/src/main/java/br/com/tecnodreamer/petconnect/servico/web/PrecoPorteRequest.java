package br.com.tecnodreamer.petconnect.servico.web;

import br.com.tecnodreamer.petconnect.pet.domain.Porte;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PrecoPorteRequest(
        @NotNull(message = "Informe o porte") Porte porte,
        @NotNull(message = "Informe o preço") @DecimalMin(value = "0.00", message = "O preço não pode ser negativo")
        @Digits(integer = 8, fraction = 2) BigDecimal preco) {
}
