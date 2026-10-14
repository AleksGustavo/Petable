package br.com.tecnodreamer.petconnect.venda.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Números calculados pelo backend sem gravar nem travar (DA-12). */
public record PreviaVendaResponse(List<Item> itens, BigDecimal subtotal, BigDecimal desconto, BigDecimal total,
        BigDecimal troco, boolean podeFinalizar) {

    /**
     * {@code disponivel} é o saldo vendável (para produto com lote, só lotes não vencidos, liberados e fora da margem).
     * {@code lotes} é a alocação FEFO que a venda faria agora; vazio para produto sem lote.
     */
    public record Item(Long produtoId, String nome, BigDecimal precoUnitario, int quantidade, BigDecimal subtotal,
            int disponivel, boolean suficiente, List<LoteAlocado> lotes) {

        public Item(Long produtoId, String nome, BigDecimal precoUnitario, int quantidade, BigDecimal subtotal,
                int disponivel, boolean suficiente) {
            this(produtoId, nome, precoUnitario, quantidade, subtotal, disponivel, suficiente, List.of());
        }
    }

    public record LoteAlocado(Long loteId, String codigoLote, LocalDate validade, int quantidade) {
    }
}
