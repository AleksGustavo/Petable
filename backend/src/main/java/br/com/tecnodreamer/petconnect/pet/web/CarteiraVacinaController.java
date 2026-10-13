package br.com.tecnodreamer.petconnect.pet.web;

import br.com.tecnodreamer.petconnect.pet.application.CarteiraVacinaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pets/{petId}/vacinas")
public class CarteiraVacinaController {

    private final CarteiraVacinaService service;

    public CarteiraVacinaController(CarteiraVacinaService service) {
        this.service = service;
    }

    @GetMapping
    public List<LancamentoVacinaResponse> listar(@PathVariable Long petId) {
        return service.listar(petId);
    }

    @PostMapping
    @PreAuthorize(PetController.PODE_EDITAR)
    @ResponseStatus(HttpStatus.CREATED)
    public LancamentoVacinaResponse registrar(@PathVariable Long petId,
            @Valid @RequestBody VacinaLancamentoRequest request) {
        return service.registrar(petId, request);
    }

    @DeleteMapping("/{lancamentoId}")
    @PreAuthorize(PetController.PODE_EDITAR)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable Long petId, @PathVariable Long lancamentoId) {
        service.remover(petId, lancamentoId);
    }
}
