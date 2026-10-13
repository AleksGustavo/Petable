package br.com.tecnodreamer.petconnect.compra.domain;

import br.com.tecnodreamer.petconnect.shared.error.ConflictException;

/** PEDIDO → RECEBIDO e PEDIDO → CANCELADO; nada mais (DA-06). */
public enum StatusCompra {
    PEDIDO, RECEBIDO, CANCELADO;

    public boolean podeIrPara(StatusCompra novo) {
        return this == PEDIDO && (novo == RECEBIDO || novo == CANCELADO);
    }

    public void exigirTransicaoPara(StatusCompra novo) {
        if (!podeIrPara(novo)) {
            throw new ConflictException("A transição de status " + this + " para " + novo + " não é permitida.");
        }
    }
}
