package br.com.tecnodreamer.petconnect.produto.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Perda ou baixa por vencimento de um lote: quantidade positiva a retirar. */
public record BaixaLoteRequest(
        @NotNull(message = "Informe a quantidade") @Min(value = 1, message = "A quantidade deve ser positiva")
        @Max(value = 1_000_000, message = "Quantidade fora do limite") Integer quantidade,
        @NotBlank(message = "Informe o motivo") @Size(min = 5, max = 500, message = "O motivo deve ter entre 5 e 500 caracteres") String motivo) {
}
