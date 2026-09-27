package br.com.tecnodreamer.petconnect.produto.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Delta: positivo é entrada, negativo é saída/correção. */
public record AjusteEstoqueRequest(
        @NotNull(message = "Informe a quantidade") @Min(value = -1_000_000, message = "Quantidade fora do limite")
        @Max(value = 1_000_000, message = "Quantidade fora do limite") Integer quantidade,
        @NotBlank(message = "Informe o motivo") @Size(min = 5, max = 500, message = "O motivo deve ter entre 5 e 500 caracteres") String motivo) {
}
