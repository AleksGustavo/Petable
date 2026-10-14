package br.com.tecnodreamer.petconnect.produto.web;

import br.com.tecnodreamer.petconnect.produto.application.LoteInformado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;

/**
 * Liga o controle de lote do produto (D11). {@code lotes} distribui o saldo atual (soma exata); lista vazia só
 * vale com saldo zero. {@code permiteSemValidade} nulo = falso; {@code diasBloqueioValidade} nulo = margem global.
 */
public record RegularizacaoLoteRequest(Boolean permiteSemValidade,
        @Min(value = 0, message = "A margem não pode ser negativa") @Max(value = 3650, message = "Margem fora do limite") Integer diasBloqueioValidade,
        List<@Valid LoteInformado> lotes) {
}
