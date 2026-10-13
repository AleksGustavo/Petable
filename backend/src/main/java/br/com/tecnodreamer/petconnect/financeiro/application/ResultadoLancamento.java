package br.com.tecnodreamer.petconnect.financeiro.application;

import br.com.tecnodreamer.petconnect.financeiro.web.LancamentoResponse;

/** {@code repetido}: a mesma Idempotency-Key já tinha criado este lançamento manual (200, sem duplicar). */
public record ResultadoLancamento(LancamentoResponse lancamento, boolean repetido) {
}
