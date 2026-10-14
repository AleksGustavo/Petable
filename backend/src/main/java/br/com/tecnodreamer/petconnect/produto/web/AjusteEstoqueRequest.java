package br.com.tecnodreamer.petconnect.produto.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Delta: positivo é entrada, negativo é saída/correção. Para produto com controle de lote, informe o lote: {@code loteId}
 * (existente) ou, só em ajuste positivo, {@code codigoLote} e {@code validade} (lote novo ou existente pelo código).
 */
public record AjusteEstoqueRequest(
        @NotNull(message = "Informe a quantidade") @Min(value = -1_000_000, message = "Quantidade fora do limite")
        @Max(value = 1_000_000, message = "Quantidade fora do limite") Integer quantidade,
        @NotBlank(message = "Informe o motivo") @Size(min = 5, max = 500, message = "O motivo deve ter entre 5 e 500 caracteres") String motivo,
        Long loteId,
        @Size(max = 50, message = "O código do lote deve ter até 50 caracteres") String codigoLote,
        LocalDate validade) {

    /** Compatível com quem não usa lote. */
    public AjusteEstoqueRequest(Integer quantidade, String motivo) {
        this(quantidade, motivo, null, null, null);
    }

    public boolean informaLote() {
        return loteId != null || (codigoLote != null && !codigoLote.isBlank()) || validade != null;
    }
}
