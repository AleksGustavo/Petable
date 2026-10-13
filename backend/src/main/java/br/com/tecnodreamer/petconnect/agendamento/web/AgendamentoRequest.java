package br.com.tecnodreamer.petconnect.agendamento.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** Não aceita status nem valor: o status muda só por transição e o preço é sempre calculado no backend. */
public record AgendamentoRequest(
        @NotNull(message = "Selecione o pet") Long petId,
        @NotNull(message = "Selecione o serviço") Long servicoId,
        Long funcionarioId,
        @NotNull(message = "Informe a data e a hora") LocalDateTime dataHora,
        @Size(max = 2000) String observacoes) {
}
