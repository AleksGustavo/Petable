package br.com.tecnodreamer.petconnect.servico.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record ServicoRequest(
        @NotBlank(message = "Informe o nome") @Size(min = 2, max = 100, message = "O nome deve ter entre 2 e 100 caracteres") String nome,
        @Size(max = 1000) String descricao,
        @NotNull(message = "Informe o preço") @DecimalMin(value = "0.00", message = "O preço não pode ser negativo")
        @Digits(integer = 8, fraction = 2) BigDecimal preco,
        @NotNull(message = "Informe a duração") @Min(value = 5, message = "A duração deve ser de 5 a 1440 minutos")
        @Max(value = 1440, message = "A duração deve ser de 5 a 1440 minutos") Integer duracaoMinutos,
        @Valid List<PrecoPorteRequest> precosPorPorte) {
}
