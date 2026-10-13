package br.com.tecnodreamer.petconnect.produto.domain;

public enum TipoMovimentacao {
    ENTRADA_COMPRA, SAIDA_VENDA, AJUSTE, ESTORNO_VENDA,
    /** Baixa por perda ou dano de um lote (D18). */
    PERDA,
    /** Baixa de lote vencido (D18). */
    VENCIMENTO,
    /**
     * Move saldo legado "sem lote" para um lote (D11): o total do produto não muda, só o saldo do lote.
     * Por isso conta em I2 (saldo do lote) mas fica fora de I1 (soma por produto).
     */
    REGULARIZACAO_LOTE
}
