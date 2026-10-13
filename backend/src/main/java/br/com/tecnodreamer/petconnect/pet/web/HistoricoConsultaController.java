package br.com.tecnodreamer.petconnect.pet.web;

import br.com.tecnodreamer.petconnect.pet.application.HistoricoConsultaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pets/{petId}/consultas")
public class HistoricoConsultaController {

    private final HistoricoConsultaService service;

    public HistoricoConsultaController(HistoricoConsultaService service) {
        this.service = service;
    }

    @GetMapping
    public List<ConsultaResponse> listar(@PathVariable Long petId) {
        return service.listar(petId);
    }

    /** SERVICO também registra: é quem atende o pet. */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDAS', 'SERVICO')")
    @ResponseStatus(HttpStatus.CREATED)
    public ConsultaResponse registrar(@PathVariable Long petId, @Valid @RequestBody ConsultaRequest request) {
        return service.registrar(petId, request);
    }
}
