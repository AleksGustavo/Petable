package br.com.tecnodreamer.petconnect.pet.web;

import br.com.tecnodreamer.petconnect.pet.domain.CarteiraVacina;
import br.com.tecnodreamer.petconnect.pet.domain.StatusVacina;

import java.time.LocalDate;

public record LancamentoVacinaResponse(Long id, CatalogoItem vacina, LocalDate dataAplicacao, LocalDate dataProxima,
        String veterinario, String lote, String observacoes, StatusVacina status, String statusDescricao) {

    public static LancamentoVacinaResponse of(CarteiraVacina l, LocalDate hoje) {
        StatusVacina status = l.status(hoje);
        return new LancamentoVacinaResponse(l.getId(), new CatalogoItem(l.getVacina().getId(), l.getVacina().getNome()),
                l.getDataAplicacao(), l.getDataProxima(), l.getVeterinario(), l.getLote(), l.getObservacoes(),
                status, status.getDescricao());
    }
}
