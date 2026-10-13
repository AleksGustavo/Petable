package br.com.tecnodreamer.petconnect.venda.web;

import java.math.BigDecimal;
import java.util.List;

/** Números calculados pelo backend sem gravar nem travar (DA-12). */
public record PreviaVendaResponse(List<Item> itens, BigDecimal subtotal, BigDecimal desconto, BigDecimal total,
        BigDecimal troco, boolean podeFinalizar) {

    public record Item(Long produtoId, String nome, BigDecimal precoUnitario, int quantidade, BigDecimal subtotal,
            int disponivel, boolean suficiente) {
    }
}
