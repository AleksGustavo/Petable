package br.com.tecnodreamer.petconnect.produto.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Lote informado pelo operador (recebimento ou regularização): código do fabricante, validade e quantidade. */
public record LoteInformado(
        @NotBlank(message = "Informe o código do lote") @Size(max = 50, message = "O código do lote deve ter até 50 caracteres") String codigoLote,
        LocalDate validade,
        @NotNull(message = "Informe a quantidade do lote") @Min(value = 1, message = "A quantidade do lote deve ser positiva")
        @Max(value = 1_000_000, message = "Quantidade fora do limite") Integer quantidade) {
}
