package br.com.tecnodreamer.petconnect.compra.web;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Etapa 06: o vencimento da conta a pagar é informado no recebimento (DA-10), nunca extraído do texto do pagamento. */
public record RecebimentoRequest(@NotNull(message = "Informe a data de entrega") LocalDate dataEntrega,
        @NotNull(message = "Informe o vencimento da conta") LocalDate dataVencimento) {
}
