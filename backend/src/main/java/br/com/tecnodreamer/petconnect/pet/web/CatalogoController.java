package br.com.tecnodreamer.petconnect.pet.web;

import br.com.tecnodreamer.petconnect.pet.application.CatalogoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Catálogos para os selects do front. Somente leitura, qualquer autenticado. */
@RestController
@RequestMapping("/api/v1/especies")
public class CatalogoController {

    private final CatalogoService service;

    public CatalogoController(CatalogoService service) {
        this.service = service;
    }

    @GetMapping
    public List<CatalogoItem> especies() {
        return service.especies();
    }

    @GetMapping("/{especieId}/racas")
    public List<CatalogoItem> racas(@PathVariable Long especieId) {
        return service.racas(especieId);
    }

    @GetMapping("/{especieId}/vacinas")
    public List<VacinaCatalogoResponse> vacinas(@PathVariable Long especieId) {
        return service.vacinas(especieId);
    }
}
