package br.com.tecnodreamer.petconnect.financeiro.domain;

/** Mesmos valores do CHECK ck_mov_categoria (V1). Só as três últimas são aceitas no lançamento manual (DA-12). */
public enum CategoriaLancamento {
    VENDA_PRODUTO, SERVICO, COMPRA_MERCADORIA, DESPESA_FUNCIONARIO, DESPESA_OPERACIONAL, OUTROS;

    public boolean manual() {
        return this == DESPESA_FUNCIONARIO || this == DESPESA_OPERACIONAL || this == OUTROS;
    }
}
