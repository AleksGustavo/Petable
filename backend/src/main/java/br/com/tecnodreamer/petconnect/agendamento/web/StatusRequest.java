package br.com.tecnodreamer.petconnect.agendamento.web;

import br.com.tecnodreamer.petconnect.venda.domain.FormaPagamento;
import br.com.tecnodreamer.petconnect.agendamento.domain.StatusAgendamento;
import br.com.tecnodreamer.petconnect.pet.web.VacinaLancamentoRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Transição de status. {@code motivo} vale para cancelamento/não
 * comparecimento; {@code valorFinal}, {@code observacoesConclusao} e
 * {@code vacina} e {@code formaPagamento} só para a conclusão (o serviço rejeita em
 * outras transições). Etapa 06: a conclusão exige forma de pagamento e caixa aberto.
 */
public record StatusRequest(
        @NotNull(message = "Informe o novo status") StatusAgendamento status,
        @Size(max = 500, message = "Use no máximo 500 caracteres") String motivo,
        @DecimalMin(value = "0.00", message = "O valor final não pode ser negativo") @Digits(integer = 8, fraction = 2)
        BigDecimal valorFinal,
        @Size(max = 2000) String observacoesConclusao,
        @Valid VacinaLancamentoRequest vacina,
        FormaPagamento formaPagamento) {

    public static StatusRequest simples(StatusAgendamento status) {
        return new StatusRequest(status, null, null, null, null, null);
    }
}
