package br.com.tecnodreamer.petconnect.produto.application;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Custo médio ponderado móvel (DA-03):
 * {@code (saldo × custoAtual + qtd × custoCompra) / (saldo + qtd)}, escala 2, HALF_UP.
 * Sem saldo anterior (ou sem custo conhecido), vale o custo da compra.
 */
@Component
public class CustoMedioService {

    public BigDecimal calcular(int saldoAnterior, BigDecimal custoAnterior, int quantidadeRecebida, BigDecimal custoCompra) {
        if (saldoAnterior <= 0 || custoAnterior == null) {
            return custoCompra.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal valorAnterior = custoAnterior.multiply(BigDecimal.valueOf(saldoAnterior));
        BigDecimal valorEntrada = custoCompra.multiply(BigDecimal.valueOf(quantidadeRecebida));
        return valorAnterior.add(valorEntrada)
                .divide(BigDecimal.valueOf((long) saldoAnterior + quantidadeRecebida), 2, RoundingMode.HALF_UP);
    }
}
