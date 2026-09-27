package br.com.tecnodreamer.petconnect.financeiro.web;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ResumoCaixaAtualResponse(boolean aberto, Long caixaId, LocalDateTime abertoEm, BigDecimal saldoEsperadoDinheiro) {
}
