package br.com.tecnodreamer.petconnect.migracao.application;

/**
 * Migra uma entidade. {@link #consulta()} recebe :ultimo e :lote (paginação por id,
 * nunca OFFSET) e só pode selecionar colunas necessárias — nunca senha, hash ou token.
 */
public interface Migrador {

    String entidade();

    String consulta();

    Desfecho migrar(Linha linha, Contexto contexto);
}
