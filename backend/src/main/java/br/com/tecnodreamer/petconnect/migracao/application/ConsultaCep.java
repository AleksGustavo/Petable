package br.com.tecnodreamer.petconnect.migracao.application;

import java.util.Optional;

/** Enriquecimento opcional de endereço (ViaCEP). Falha ou ausência nunca interrompe a migração. */
public interface ConsultaCep {

    record Localidade(String cidade, String uf) {
    }

    Optional<Localidade> consultar(String cep);
}
