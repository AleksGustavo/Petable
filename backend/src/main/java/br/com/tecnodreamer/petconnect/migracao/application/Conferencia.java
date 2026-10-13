package br.com.tecnodreamer.petconnect.migracao.application;

/** Resultado de uma conferência automática; {@code detalhe} só traz números. */
public record Conferencia(String nome, boolean ok, String detalhe) {
}
