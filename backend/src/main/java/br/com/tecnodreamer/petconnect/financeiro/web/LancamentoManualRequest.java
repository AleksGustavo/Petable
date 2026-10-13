package br.com.tecnodreamer.petconnect.financeiro.web;

import br.com.tecnodreamer.petconnect.financeiro.domain.CategoriaLancamento;
import br.com.tecnodreamer.petconnect.financeiro.domain.TipoLancamento;
import br.com.tecnodreamer.petconnect.venda.domain.FormaPagamento;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Lançamento manual (DA-12): categoria só DESPESA_FUNCIONARIO, DESPESA_OPERACIONAL ou OUTROS (conferido no serviço). */
public record LancamentoManualRequest(
        @NotNull(message = "Selecione o tipo") TipoLancamento tipo,
        @NotNull(message = "Selecione a categoria") CategoriaLancamento categoria,
        @NotBlank(message = "Informe a descrição") @Size(min = 3, max = 100, message = "A descrição deve ter entre 3 e 100 caracteres")
        String descricao,
        @NotNull(message = "Informe o valor") @DecimalMin(value = "0.00", inclusive = false, message = "O valor deve ser maior que zero")
        @DecimalMax(value = "99999999.99", message = "Valor acima do limite")
        @Digits(integer = 8, fraction = 2, message = "Use no máximo duas casas decimais") BigDecimal valor,
        @NotNull(message = "Selecione a forma de pagamento") FormaPagamento formaPagamento,
        @Size(max = 2000) String observacoes) {
}
