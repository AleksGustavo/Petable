package br.com.tecnodreamer.petconnect.pet.web;

import br.com.tecnodreamer.petconnect.pet.domain.Vacina;

/**
 * {@code baixaEstoque}: aplicar esta vacina tira uma dose do estoque (produto ligado, com controle de lote, e a flag de lotes
 * ligada). A tela então não pede o lote: ele vem do estoque.
 */
public record VacinaCatalogoResponse(Long id, String nome, String doencaProtecao, int validadePadraoMeses, boolean baixaEstoque) {

    public static VacinaCatalogoResponse of(Vacina v, boolean baixaEstoque) {
        return new VacinaCatalogoResponse(v.getId(), v.getNome(), v.getDoencaProtecao(), v.getValidadePadraoMeses(), baixaEstoque);
    }
}
