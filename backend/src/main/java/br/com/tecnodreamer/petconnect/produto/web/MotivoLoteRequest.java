package br.com.tecnodreamer.petconnect.produto.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Bloqueio e desbloqueio de lote exigem motivo (vai para a auditoria). */
public record MotivoLoteRequest(
        @NotBlank(message = "Informe o motivo") @Size(min = 5, max = 500, message = "O motivo deve ter entre 5 e 500 caracteres") String motivo) {
}
