package br.com.tecnodreamer.petconnect.pet.web;

import br.com.tecnodreamer.petconnect.pet.domain.Vacina;

/** Vacina do catálogo com o produto de estoque ligado (nulo = a aplicação não baixa estoque). */
public record VacinaEstoqueResponse(Long id, String nome, String especie, CatalogoItem produto) {

    public static VacinaEstoqueResponse of(Vacina v) {
        var p = v.getProduto();
        return new VacinaEstoqueResponse(v.getId(), v.getNome(), v.getEspecie().getNome(),
                p == null ? null : new CatalogoItem(p.getId(), p.getNome()));
    }
}
