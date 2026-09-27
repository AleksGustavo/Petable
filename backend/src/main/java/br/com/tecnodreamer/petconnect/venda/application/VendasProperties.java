package br.com.tecnodreamer.petconnect.venda.application;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

/** {@code petconnect.vendas.desconto-maximo-percentual-vendas}: teto de desconto do papel VENDAS (DA-03). */
@Validated
@ConfigurationProperties("petconnect.vendas")
public record VendasProperties(
        @NotNull @DecimalMin("0") @DecimalMax("100") @DefaultValue("10") BigDecimal descontoMaximoPercentualVendas) {
}
