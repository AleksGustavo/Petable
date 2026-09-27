package br.com.tecnodreamer.petconnect.pet.infrastructure;

import br.com.tecnodreamer.petconnect.pet.domain.TipoImagem;

/**
 * Armazenamento das fotos. Hoje é disco local ({@link LocalFotoPetStorage});
 * a interface permite trocar por um storage externo sem mexer no serviço.
 */
public interface FotoPetStorage {

    /** Grava o conteúdo com um nome interno gerado e devolve a chave. */
    String salvar(byte[] conteudo, TipoImagem tipo);

    byte[] ler(String chave);

    void remover(String chave);
}
