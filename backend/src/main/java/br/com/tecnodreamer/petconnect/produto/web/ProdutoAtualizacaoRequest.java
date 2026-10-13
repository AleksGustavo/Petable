package br.com.tecnodreamer.petconnect.produto.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Edição: sem saldo, sem custo (só o recebimento de compra muda o custo) e sem ativo. */
public record ProdutoAtualizacaoRequest(
        @NotBlank(message = "Informe o nome") @Size(min = 2, max = 100, message = "O nome deve ter entre 2 e 100 caracteres") String nome,
        @Size(max = 2000) String descricao,
        @NotNull(message = "Selecione a categoria") Long categoriaId,
        Long fornecedorPadraoId,
        @Pattern(regexp = "\\d{8,14}", message = "O código de barras deve ter de 8 a 14 dígitos") String codigoBarras,
        @NotNull(message = "Informe o preço de venda") @DecimalMin(value = "0.00", inclusive = false, message = "O preço de venda deve ser maior que zero")
        @Digits(integer = 8, fraction = 2) BigDecimal precoVenda,
        @NotNull(message = "Informe o estoque mínimo") @Min(value = 0, message = "O estoque mínimo não pode ser negativo")
        @Max(value = 1_000_000, message = "O estoque mínimo deve ser no máximo 1.000.000") Integer estoqueMinimo) {
}
