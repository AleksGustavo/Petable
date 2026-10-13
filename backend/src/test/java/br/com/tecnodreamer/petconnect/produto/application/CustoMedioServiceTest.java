package br.com.tecnodreamer.petconnect.produto.application;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CustoMedioServiceTest {

    private final CustoMedioService service = new CustoMedioService();

    @Test
    void deveCalcularCustoMedioPonderado() {
        // Exemplo da spec: 10 a R$ 20,00 + 5 a R$ 26,00 = R$ 22,00.
        assertThat(service.calcular(10, new BigDecimal("20.00"), 5, new BigDecimal("26.00"))).isEqualByComparingTo("22.00");
    }

    @Test
    void deveUsarCustoDaCompraQuandoSaldoAnteriorForZero() {
        assertThat(service.calcular(0, new BigDecimal("50.00"), 3, new BigDecimal("31.5"))).isEqualTo(new BigDecimal("31.50"));
        assertThat(service.calcular(4, null, 3, new BigDecimal("12.00"))).isEqualTo(new BigDecimal("12.00"));
    }

    @Test
    void deveArredondarCustoParaDuasCasasHalfUp() {
        // (10,00 + 10,01) / 2 = 10,005 → HALF_UP = 10,01 (HALF_EVEN daria 10,00).
        assertThat(service.calcular(1, new BigDecimal("10.00"), 1, new BigDecimal("10.01"))).isEqualTo(new BigDecimal("10.01"));
        // (10,00 + 2 × 10,01) / 3 = 10,00666… → 10,01.
        assertThat(service.calcular(1, new BigDecimal("10.00"), 2, new BigDecimal("10.01"))).isEqualTo(new BigDecimal("10.01"));
    }
}
