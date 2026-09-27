package br.com.tecnodreamer.petconnect.relatorio.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Cálculos puros dos relatórios (testados isoladamente). Dinheiro sempre BigDecimal, HALF_UP. */
public final class RelatorioCalculos {

    static final BigDecimal CEM = BigDecimal.valueOf(100);
    private static final BigDecimal LIMITE_A = BigDecimal.valueOf(80);
    private static final BigDecimal LIMITE_B = BigDecimal.valueOf(95);

    private RelatorioCalculos() {
    }

    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2);
    }

    public static BigDecimal dinheiro(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    /** Ticket médio = líquido / quantidade; sem vendas, 0. */
    public static BigDecimal ticketMedio(BigDecimal liquido, long quantidade) {
        return quantidade == 0 ? zero() : dinheiro(liquido).divide(BigDecimal.valueOf(quantidade), 2, RoundingMode.HALF_UP);
    }

    /** Participação percentual (2 casas); total zero → 0. */
    public static BigDecimal percentual(BigDecimal parte, BigDecimal total) {
        if (total == null || total.signum() == 0) {
            return zero();
        }
        return dinheiro(parte).multiply(CEM).divide(total, 2, RoundingMode.HALF_UP);
    }

    /**
     * Classe ABC pelo percentual acumulado ANTES do item (DA-05): &lt; 80 → A, &lt; 95 → B, senão C.
     * Assim o item que cruza 80% ainda é A.
     */
    public static String classeAbc(BigDecimal acumuladoAnterior) {
        if (acumuladoAnterior.compareTo(LIMITE_A) < 0) {
            return "A";
        }
        return acumuladoAnterior.compareTo(LIMITE_B) < 0 ? "B" : "C";
    }

    /** Item com faturamento e a classificação calculada. */
    public record ItemAbc<T>(T item, BigDecimal faturamento, BigDecimal participacao, BigDecimal acumulado, String classe) {
    }

    /** Classifica itens já ordenados por faturamento decrescente. */
    public static <T> List<ItemAbc<T>> curvaAbc(List<T> itensOrdenados, Function<T, BigDecimal> faturamento) {
        BigDecimal total = itensOrdenados.stream().map(faturamento).map(RelatorioCalculos::dinheiro)
                .reduce(zero(), BigDecimal::add);
        List<ItemAbc<T>> r = new ArrayList<>();
        BigDecimal acumuladoValor = zero();
        for (T item : itensOrdenados) {
            BigDecimal f = dinheiro(faturamento.apply(item));
            String classe = classeAbc(percentual(acumuladoValor, total));
            acumuladoValor = acumuladoValor.add(f);
            r.add(new ItemAbc<>(item, f, percentual(f, total), percentual(acumuladoValor, total), classe));
        }
        return r;
    }

    /** Série diária com todos os dias do período (DA-08); dias ausentes recebem {@code vazio}. */
    public static <T> List<T> preencherDias(Periodo p, Map<LocalDate, T> porDia, Function<LocalDate, T> vazio) {
        Map<LocalDate, T> ordenado = new LinkedHashMap<>();
        for (LocalDate d = p.inicio(); !d.isAfter(p.fim()); d = d.plusDays(1)) {
            ordenado.put(d, porDia.getOrDefault(d, vazio.apply(d)));
        }
        return new ArrayList<>(ordenado.values());
    }

    /** Giro aproximado = vendida / saldo atual; sem saldo, nulo (DA-06). */
    public static BigDecimal giro(long vendida, long saldoAtual) {
        return saldoAtual <= 0 ? null
                : BigDecimal.valueOf(vendida).divide(BigDecimal.valueOf(saldoAtual), 2, RoundingMode.HALF_UP);
    }

    /** Cobertura em dias = saldo / (vendida / dias); sem venda, nulo. */
    public static BigDecimal coberturaDias(long saldoAtual, long vendida, long dias) {
        if (vendida <= 0) {
            return null;
        }
        return BigDecimal.valueOf(saldoAtual).multiply(BigDecimal.valueOf(dias))
                .divide(BigDecimal.valueOf(vendida), 1, RoundingMode.HALF_UP);
    }

    /** Margem % = margem / receita; receita zero → 0. */
    public static BigDecimal margemPercentual(BigDecimal margem, BigDecimal receita) {
        return percentual(margem, receita);
    }
}
