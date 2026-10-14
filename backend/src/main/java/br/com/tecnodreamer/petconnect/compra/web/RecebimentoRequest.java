package br.com.tecnodreamer.petconnect.compra.web;

import br.com.tecnodreamer.petconnect.produto.application.LoteInformado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * Etapa 06: o vencimento da conta a pagar é informado no recebimento (DA-10), nunca extraído do texto do pagamento.
 * Lotes, etapa 03: {@code lotes} traz, por produto controlado, os lotes recebidos (soma = quantidade do item, D17).
 * Produto sem controle de lote não aparece aqui.
 */
public record RecebimentoRequest(@NotNull(message = "Informe a data de entrega") LocalDate dataEntrega,
        @NotNull(message = "Informe o vencimento da conta") LocalDate dataVencimento,
        List<@Valid LotesDoItem> lotes) {

    public RecebimentoRequest(LocalDate dataEntrega, LocalDate dataVencimento) {
        this(dataEntrega, dataVencimento, null);
    }

    public record LotesDoItem(@NotNull(message = "Informe o produto") Long produtoId,
            @NotNull(message = "Informe os lotes") List<@Valid LoteInformado> lotes) {
    }
}
