package br.com.tecnodreamer.petconnect.agendamento.web;

import br.com.tecnodreamer.petconnect.pet.domain.Porte;
import br.com.tecnodreamer.petconnect.servico.domain.OrigemPreco;

import java.math.BigDecimal;

/** Prévia informativa: o POST recalcula e nunca confia no valor vindo do front. */
public record PrecoResponse(Long petId, Long servicoId, Porte porte, OrigemPreco origemPreco,
        BigDecimal totalEstimado, Integer duracaoMinutos) {
}
