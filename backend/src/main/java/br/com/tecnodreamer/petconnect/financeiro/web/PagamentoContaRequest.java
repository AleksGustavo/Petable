package br.com.tecnodreamer.petconnect.financeiro.web;

import br.com.tecnodreamer.petconnect.venda.domain.FormaPagamento;
import jakarta.validation.constraints.NotNull;

public record PagamentoContaRequest(@NotNull(message = "Selecione a forma de pagamento") FormaPagamento formaPagamento) {
}
