package br.com.tecnodreamer.petconnect.venda.domain;

/**
 * Destino físico do produto no cancelamento de uma venda com lote (D9). Sem valor padrão: o operador escolhe.
 * O dinheiro é estornado nos dois casos.
 */
public enum DestinoEstoque {
    /** Volta ao lote original (produto em condição de revenda). */
    RETORNAR,
    /** Entra e sai do lote como perda (produto aberto, danificado ou sem cadeia de frio). */
    DESCARTAR
}
