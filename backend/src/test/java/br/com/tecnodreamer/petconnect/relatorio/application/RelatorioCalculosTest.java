package br.com.tecnodreamer.petconnect.relatorio.application;

import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RelatorioCalculosTest {

    @Test
    void ticketMedioComZeroVendasDeveSerZero() {
        assertThat(RelatorioCalculos.ticketMedio(BigDecimal.ZERO, 0)).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void ticketMedioArredondaHalfUp() {
        // 150,00 / 2 = 75,00; 100,00 / 3 = 33,333… → 33,33; 100,01 / 2 = 50,005 → 50,01
        assertThat(RelatorioCalculos.ticketMedio(new BigDecimal("150.00"), 2)).isEqualTo(new BigDecimal("75.00"));
        assertThat(RelatorioCalculos.ticketMedio(new BigDecimal("100.00"), 3)).isEqualTo(new BigDecimal("33.33"));
        assertThat(RelatorioCalculos.ticketMedio(new BigDecimal("100.01"), 2)).isEqualTo(new BigDecimal("50.01"));
    }

    @Test
    void deveClassificarCurvaAbcPeloAcumuladoAnterior() {
        // Exemplo da spec: 700, 200, 60, 40 (total 1000) → acumulado antes 0% A, 70% A, 90% B, 96% C.
        List<RelatorioCalculos.ItemAbc<String>> abc = RelatorioCalculos.curvaAbc(List.of("a", "b", "c", "d"),
                s -> switch (s) {
                    case "a" -> new BigDecimal("700");
                    case "b" -> new BigDecimal("200");
                    case "c" -> new BigDecimal("60");
                    default -> new BigDecimal("40");
                });

        assertThat(abc).extracting(RelatorioCalculos.ItemAbc::classe).containsExactly("A", "A", "B", "C");
        assertThat(abc.get(1).acumulado()).isEqualByComparingTo("90.00");
        assertThat(abc.get(3).participacao()).isEqualByComparingTo("4.00");
    }

    @Test
    void curvaAbcVaziaNaoFalha() {
        assertThat(RelatorioCalculos.curvaAbc(List.<String>of(), s -> BigDecimal.ONE)).isEmpty();
        assertThat(RelatorioCalculos.percentual(BigDecimal.TEN, BigDecimal.ZERO)).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void devePreencherDiasSemMovimentoComZero() {
        Periodo p = Periodo.validar(LocalDate.of(2031, 3, 1), LocalDate.of(2031, 3, 4));
        List<String> serie = RelatorioCalculos.preencherDias(p, Map.of(LocalDate.of(2031, 3, 2), "venda"), d -> "zero " + d.getDayOfMonth());

        assertThat(serie).containsExactly("zero 1", "venda", "zero 3", "zero 4");
    }

    @Test
    void giroECoberturaNulosSemSaldoOuSemVenda() {
        assertThat(RelatorioCalculos.giro(10, 0)).isNull();
        assertThat(RelatorioCalculos.giro(30, 12)).isEqualByComparingTo("2.50");
        assertThat(RelatorioCalculos.coberturaDias(12, 0, 30)).isNull();
        // 12 em estoque, 30 vendidas em 30 dias (1/dia) → 12 dias
        assertThat(RelatorioCalculos.coberturaDias(12, 30, 30)).isEqualByComparingTo("12.0");
    }

    @Test
    void margemPercentualComReceitaZeroDeveSerZero() {
        assertThat(RelatorioCalculos.margemPercentual(new BigDecimal("-5.00"), BigDecimal.ZERO)).isEqualTo(new BigDecimal("0.00"));
        assertThat(RelatorioCalculos.margemPercentual(new BigDecimal("40.00"), new BigDecimal("100.00"))).isEqualByComparingTo("40.00");
    }

    @Test
    void deveRejeitarPeriodoInvertido() {
        assertThatThrownBy(() -> Periodo.validar(LocalDate.of(2031, 3, 2), LocalDate.of(2031, 3, 1)))
                .isInstanceOf(CampoInvalidoException.class).extracting("campo").isEqualTo("dataFim");
        assertThatThrownBy(() -> Periodo.validar(null, LocalDate.of(2031, 3, 1)))
                .isInstanceOf(CampoInvalidoException.class).extracting("campo").isEqualTo("dataInicio");
    }

    @Test
    void deveRejeitarPeriodoMaiorQue366Dias() {
        assertThat(Periodo.validar(LocalDate.of(2031, 1, 1), LocalDate.of(2032, 1, 1)).dias()).isEqualTo(366);
        assertThatThrownBy(() -> Periodo.validar(LocalDate.of(2031, 1, 1), LocalDate.of(2032, 1, 2)))
                .isInstanceOf(CampoInvalidoException.class).hasMessageContaining("366");
    }

    @Test
    void deveAceitarPeriodoDeUmDia() {
        Periodo p = Periodo.validar(LocalDate.of(2031, 3, 1), LocalDate.of(2031, 3, 1));
        assertThat(p.dias()).isEqualTo(1);
        assertThat(p.fimExclusivo()).isEqualTo(LocalDate.of(2031, 3, 2).atStartOfDay());
    }
}
