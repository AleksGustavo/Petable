package br.com.tecnodreamer.petconnect.fornecedor.web;

import br.com.tecnodreamer.petconnect.fornecedor.application.FornecedorService;
import br.com.tecnodreamer.petconnect.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

/** Leitura: ADMIN e VENDAS. Escrita: só ADMIN. SERVICO não acessa (DA-13). */
@RestController
@RequestMapping("/api/v1/fornecedores")
@PreAuthorize("hasAnyRole('ADMIN', 'VENDAS')")
public class FornecedorController {

    private static final String ADMIN = "hasRole('ADMIN')";

    private final FornecedorService service;

    public FornecedorController(FornecedorService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<FornecedorResponse> listar(@RequestParam(required = false) @Size(max = 120) String q,
            @RequestParam(required = false) Boolean ativo,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.of(service.listar(q, ativo, PageRequest.of(page, size, Sort.by("nomeFantasia", "id"))));
    }

    @GetMapping("/opcoes")
    public List<FornecedorOpcao> opcoes(@RequestParam(required = false) @Size(max = 120) String q) {
        return service.opcoes(q);
    }

    @GetMapping("/{id}")
    public FornecedorResponse buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping
    @PreAuthorize(ADMIN)
    public ResponseEntity<FornecedorResponse> criar(@Valid @RequestBody FornecedorRequest request) {
        FornecedorResponse criado = service.criar(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(criado.id());
        return ResponseEntity.created(location).body(criado);
    }

    @PutMapping("/{id}")
    @PreAuthorize(ADMIN)
    public FornecedorResponse atualizar(@PathVariable Long id, @Valid @RequestBody FornecedorRequest request) {
        return service.atualizar(id, request);
    }

    @PatchMapping("/{id}/desativar")
    @PreAuthorize(ADMIN)
    public FornecedorResponse desativar(@PathVariable Long id) {
        return service.desativar(id);
    }

    @PatchMapping("/{id}/ativar")
    @PreAuthorize(ADMIN)
    public FornecedorResponse ativar(@PathVariable Long id) {
        return service.ativar(id);
    }
}
