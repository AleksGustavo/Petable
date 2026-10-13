package br.com.tecnodreamer.petconnect.financeiro.web;

import br.com.tecnodreamer.petconnect.venda.domain.FormaPagamento;

import java.math.BigDecimal;
import java.util.Map;

public record PreviaFechamentoResponse(Long caixaId, Map<FormaPagamento, BigDecimal> esperado, BigDecimal totalEsperado) {
}
