package br.com.tecnodreamer.petconnect.usuario.infrastructure;

/** Limite de pedidos de "esqueci a senha". A implementação atual é em memória; trocar por Redis com várias réplicas. */
public interface LimitadorRedefinicao {

    /** Registra a tentativa e diz se ela está dentro do limite (por e-mail e por IP). */
    boolean permitir(String email, String ip);
}
