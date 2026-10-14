package br.com.tecnodreamer.petconnect.venda.web;

import br.com.tecnodreamer.petconnect.venda.domain.DestinoEstoque;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code destinoEstoque} é obrigatório quando algum item da venda saiu de lote (D9) e não tem valor padrão;
 * em venda só de produtos sem lote é ignorado.
 */
public record CancelamentoVendaRequest(
        @NotBlank(message = "Informe o motivo")
        @Size(min = 5, max = 500, message = "O motivo deve ter entre 5 e 500 caracteres") String motivo,
        DestinoEstoque destinoEstoque) {

    public CancelamentoVendaRequest(String motivo) {
        this(motivo, null);
    }
}
