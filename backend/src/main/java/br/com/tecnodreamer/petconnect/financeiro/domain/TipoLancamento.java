package br.com.tecnodreamer.petconnect.financeiro.domain;

public enum TipoLancamento {
    ENTRADA, SAIDA;

    public TipoLancamento inverso() {
        return this == ENTRADA ? SAIDA : ENTRADA;
    }
}
