package br.com.tecnodreamer.petconnect.pet.web;

import br.com.tecnodreamer.petconnect.pet.domain.Vacina;

public record VacinaCatalogoResponse(Long id, String nome, String doencaProtecao, int validadePadraoMeses) {

    public static VacinaCatalogoResponse of(Vacina v) {
        return new VacinaCatalogoResponse(v.getId(), v.getNome(), v.getDoencaProtecao(), v.getValidadePadraoMeses());
    }
}
