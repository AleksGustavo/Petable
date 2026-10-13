package br.com.tecnodreamer.petconnect.financeiro.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FluxoResponse(LocalDate dataInicio, LocalDate dataFim, BigDecimal entradas, BigDecimal saidas, BigDecimal saldo,
        List<PorCategoria> porCategoria, List<PorForma> porFormaPagamento) {

    public record PorCategoria(String categoria, BigDecimal entradas, BigDecimal saidas, BigDecimal saldo) {
    }

    /** {@code formaPagamento} "NAO_INFORMADA" para lançamentos históricos sem forma. */
    public record PorForma(String formaPagamento, BigDecimal entradas, BigDecimal saidas, BigDecimal saldo) {
    }
}
