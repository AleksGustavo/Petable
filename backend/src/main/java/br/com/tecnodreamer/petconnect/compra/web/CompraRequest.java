package br.com.tecnodreamer.petconnect.compra.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Não aceita total, status, data de entrega nem subtotal: o backend calcula e controla. */
public record CompraRequest(
        @NotNull(message = "Selecione o fornecedor") Long fornecedorId,
        Long funcionarioId,
        @NotNull(message = "Informe a data da compra") LocalDate dataCompra,
        @NotBlank(message = "Informe a forma de pagamento") @Size(min = 2, max = 50, message = "A forma de pagamento deve ter entre 2 e 50 caracteres") String formaPagamento,
        @Size(max = 2000) String observacoes,
        @NotEmpty(message = "Adicione pelo menos um item") @Size(max = 100, message = "A compra pode ter no máximo 100 itens")
        List<@Valid Item> itens) {

    public record Item(
            @NotNull(message = "Selecione o produto") Long produtoId,
            @NotNull(message = "Informe a quantidade") @Min(value = 1, message = "A quantidade deve ser de 1 a 1.000.000")
            @Max(value = 1_000_000, message = "A quantidade deve ser de 1 a 1.000.000") Integer quantidade,
            @NotNull(message = "Informe o custo unitário") @DecimalMin(value = "0.00", inclusive = false, message = "O custo unitário deve ser maior que zero")
            @Digits(integer = 8, fraction = 2, message = "Use no máximo duas casas decimais") BigDecimal precoCustoUnitario) {
    }
}
