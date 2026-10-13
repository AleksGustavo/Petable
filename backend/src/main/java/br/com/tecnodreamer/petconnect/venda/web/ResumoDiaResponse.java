package br.com.tecnodreamer.petconnect.venda.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ResumoDiaResponse(LocalDate data, long quantidade, BigDecimal total) {
}
