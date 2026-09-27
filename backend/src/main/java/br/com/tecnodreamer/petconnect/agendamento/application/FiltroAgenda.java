package br.com.tecnodreamer.petconnect.agendamento.application;

import br.com.tecnodreamer.petconnect.agendamento.domain.StatusAgendamento;

import java.time.LocalDate;

public record FiltroAgenda(LocalDate inicio, LocalDate fim, StatusAgendamento status, Long petId, Long clienteId,
        Long servicoId, String termo) {
}
