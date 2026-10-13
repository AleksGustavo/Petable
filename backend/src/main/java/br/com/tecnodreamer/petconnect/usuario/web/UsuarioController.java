package br.com.tecnodreamer.petconnect.usuario.web;

import br.com.tecnodreamer.petconnect.shared.security.UsuarioAtual;
import br.com.tecnodreamer.petconnect.shared.web.PageResponse;
import br.com.tecnodreamer.petconnect.usuario.application.UsuarioService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
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

@RestController
@RequestMapping("/api/v1/usuarios")
@PreAuthorize("hasRole('ADMIN')")
public class UsuarioController {

    private final UsuarioService service;

    public UsuarioController(UsuarioService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<UsuarioResponse> listar(@RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) Boolean ativo,
            @RequestParam(required = false) @Pattern(regexp = "ADMIN|VENDAS|SERVICO") String papel,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.of(service.listar(q, ativo, papel, PageRequest.of(page, size, Sort.by("username"))));
    }

    @GetMapping("/{id}")
    public UsuarioResponse buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping
    public ResponseEntity<UsuarioResponse> criar(@Valid @RequestBody UsuarioRequest request) {
        UsuarioResponse criado = service.criar(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(criado.id());
        return ResponseEntity.created(location).body(criado);
    }

    @PutMapping("/{id}")
    public UsuarioResponse atualizar(@PathVariable Long id, @Valid @RequestBody ContaRequest request) {
        return service.atualizarConta(id, request, "USUARIO_ATUALIZADO");
    }

    @PatchMapping("/{id}/papel")
    public UsuarioResponse alterarPapel(@PathVariable Long id, @Valid @RequestBody PapelRequest request) {
        return service.alterarPapel(id, request.papel(), UsuarioAtual.id().orElse(null));
    }

    @PatchMapping("/{id}/desativar")
    public UsuarioResponse desativar(@PathVariable Long id) {
        return service.desativar(id, UsuarioAtual.id().orElse(null));
    }

    @PatchMapping("/{id}/ativar")
    public UsuarioResponse ativar(@PathVariable Long id) {
        return service.ativar(id);
    }
}
