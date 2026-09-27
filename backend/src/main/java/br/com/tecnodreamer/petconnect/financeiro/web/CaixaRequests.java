package br.com.tecnodreamer.petconnect.financeiro.web;

import br.com.tecnodreamer.petconnect.venda.domain.FormaPagamento;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

/** Requests do caixa. */
public final class CaixaRequests {

    private CaixaRequests() {
    }

    public record Abertura(
            @NotNull(message = "Informe o fundo de troco (pode ser zero)")
            @DecimalMin(value = "0.00", message = "O fundo não pode ser negativo")
            @DecimalMax(value = "99999999.99", message = "Valor acima do limite")
            @Digits(integer = 8, fraction = 2, message = "Use no máximo duas casas decimais") BigDecimal fundoAbertura) {
    }

    public record Movimento(
            @NotNull(message = "Informe o valor") @DecimalMin(value = "0.00", inclusive = false, message = "O valor deve ser maior que zero")
            @DecimalMax(value = "99999999.99", message = "Valor acima do limite")
            @Digits(integer = 8, fraction = 2, message = "Use no máximo duas casas decimais") BigDecimal valor,
            @NotBlank(message = "Informe o motivo")
            @Size(min = 5, max = 500, message = "O motivo deve ter entre 5 e 500 caracteres") String motivo) {
    }

    /** Valor contado por forma (as cinco obrigatórias); motivo obrigatório se houver diferença (conferido no serviço). */
    public record Fechamento(
            @NotNull(message = "Informe os valores contados") Map<FormaPagamento, BigDecimal> contado,
            @Size(max = 500, message = "Use no máximo 500 caracteres") String motivoDiferenca) {
    }
}
