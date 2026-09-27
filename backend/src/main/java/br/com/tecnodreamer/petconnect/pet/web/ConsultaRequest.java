package br.com.tecnodreamer.petconnect.pet.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** Veterinário limitado a 100 caracteres: é o tamanho da coluna na V1. */
public record ConsultaRequest(
        @NotNull(message = "Informe a data da consulta") LocalDateTime dataConsulta,
        @NotBlank(message = "Informe o veterinário") @Size(min = 2, max = 100, message = "O veterinário deve ter entre 2 e 100 caracteres") String veterinario,
        @Size(max = 2000) String motivo,
        @Size(max = 2000) String diagnostico,
        @Size(max = 2000) String tratamento,
        @Size(max = 2000) String observacoes) {
}
