package br.com.tecnodreamer.petconnect.servico.web;

import br.com.tecnodreamer.petconnect.servico.application.ServicoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/v1/servicos")
public class ServicoController {

    private static final String ADMIN = "hasRole('ADMIN')";

    private final ServicoService service;

    public ServicoController(ServicoService service) {
        this.service = service;
    }

    @GetMapping
    public List<ServicoResponse> listar(@RequestParam(required = false) Boolean ativo) {
        return service.listar(ativo);
    }

    @GetMapping("/{id}")
    public ServicoResponse buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping
    @PreAuthorize(ADMIN)
    public ResponseEntity<ServicoResponse> criar(@Valid @RequestBody ServicoRequest request) {
        ServicoResponse criado = service.criar(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(criado.id());
        return ResponseEntity.created(location).body(criado);
    }

    @PutMapping("/{id}")
    @PreAuthorize(ADMIN)
    public ServicoResponse atualizar(@PathVariable Long id, @Valid @RequestBody ServicoRequest request) {
        return service.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(ADMIN)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desativar(@PathVariable Long id) {
        service.desativar(id);
    }
}
