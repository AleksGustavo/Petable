package br.com.tecnodreamer.petconnect.compra.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelamentoCompraRequest(
        @NotBlank(message = "Informe o motivo") @Size(min = 5, max = 500, message = "O motivo deve ter entre 5 e 500 caracteres") String motivo) {
}
