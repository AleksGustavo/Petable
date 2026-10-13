package br.com.tecnodreamer.petconnect.produto.domain;

/** DISPONIVEL entra no FEFO; BLOQUEADO (quarentena, recolhimento) fica visível mas nunca é vendido. */
public enum StatusLote {
    DISPONIVEL, BLOQUEADO
}
