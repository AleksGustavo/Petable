package br.com.tecnodreamer.petconnect.venda.application;

import br.com.tecnodreamer.petconnect.venda.web.VendaResponse;

/** {@code repetida}: a mesma Idempotency-Key já tinha gerado esta venda (responde 200, sem nova baixa). */
public record ResultadoVenda(VendaResponse venda, boolean repetida) {
}
