package br.com.tecnodreamer.petconnect.pet.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record VacinaLancamentoRequest(
        @NotNull(message = "Selecione a vacina") Long vacinaId,
        @NotNull(message = "Informe a data de aplicação") LocalDate dataAplicacao,
        LocalDate dataProxima,
        @Size(max = 100) String veterinario,
        @Size(max = 50) String lote,
        @Size(max = 2000) String observacoes) {
}
