package br.com.tecnodreamer.petconnect.financeiro.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ResumoFinanceiroDiaResponse(LocalDate data, BigDecimal entradas, BigDecimal saidas, BigDecimal saldo,
        long contasVencidas, BigDecimal valorContasVencidas, long contasAVencer7Dias, BigDecimal valorAVencer7Dias,
        long caixasAbertos) {
}
