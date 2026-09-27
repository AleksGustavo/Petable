package br.com.tecnodreamer.petconnect.venda.web;

import br.com.tecnodreamer.petconnect.venda.domain.FormaPagamento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Venda (e prévia). Não aceita preço, subtotal, total, status, data nem operador:
 * o backend calcula e controla (DA-02, DA-08).
 */
public record VendaRequest(
        Long clienteId,
        @NotEmpty(message = "Adicione pelo menos um item") @Size(max = 100, message = "A venda pode ter no máximo 100 itens")
        List<@Valid Item> itens,
        @DecimalMin(value = "0.00", message = "O desconto não pode ser negativo")
        @Digits(integer = 8, fraction = 2, message = "Use no máximo duas casas decimais") BigDecimal desconto,
        @NotNull(message = "Selecione a forma de pagamento") FormaPagamento formaPagamento,
        @DecimalMin(value = "0.00", message = "O valor recebido não pode ser negativo")
        @Digits(integer = 8, fraction = 2, message = "Use no máximo duas casas decimais") BigDecimal valorRecebido,
        @Size(max = 2000) String observacoes) {

    public record Item(
            @NotNull(message = "Selecione o produto") Long produtoId,
            @NotNull(message = "Informe a quantidade") @Min(value = 1, message = "A quantidade deve ser de 1 a 10.000")
            @Max(value = 10_000, message = "A quantidade deve ser de 1 a 10.000") Integer quantidade) {
    }
}
