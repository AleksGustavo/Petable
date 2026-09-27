package br.com.tecnodreamer.petconnect.migracao.application;

/** Pendência do relatório: só entidade, id LEGADO e código do motivo. */
public record Pendencia(String entidade, Long idLegado, String codigo) {
}
