package br.com.tecnodreamer.petconnect.cliente.web;

import br.com.tecnodreamer.petconnect.cliente.application.ClienteService;
import br.com.tecnodreamer.petconnect.shared.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

@RestController
@RequestMapping("/api/v1/clientes")
public class ClienteController {

    private static final String PODE_EDITAR = "hasAnyRole('ADMIN', 'VENDAS')";

    private final ClienteService service;

    public ClienteController(ClienteService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ClienteResponse> listar(@RequestParam(required = false) String busca,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by("nome"));
        return PageResponse.of(service.listar(busca, pageable));
    }

    @GetMapping("/{id}")
    public ClienteResponse buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping
    @PreAuthorize(PODE_EDITAR)
    public ResponseEntity<ClienteResponse> criar(@Valid @RequestBody ClienteRequest request) {
        ClienteResponse criado = service.criar(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(criado.id());
        return ResponseEntity.created(location).body(criado);
    }

    @PutMapping("/{id}")
    @PreAuthorize(PODE_EDITAR)
    public ClienteResponse atualizar(@PathVariable Long id, @Valid @RequestBody ClienteRequest request) {
        return service.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(PODE_EDITAR)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void inativar(@PathVariable Long id) {
        service.inativar(id);
    }
}
