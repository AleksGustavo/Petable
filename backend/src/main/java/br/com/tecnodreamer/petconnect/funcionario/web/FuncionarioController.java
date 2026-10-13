package br.com.tecnodreamer.petconnect.funcionario.web;

import br.com.tecnodreamer.petconnect.funcionario.application.FuncionarioService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/v1/funcionarios")
public class FuncionarioController {

    private static final String ADMIN = "hasRole('ADMIN')";

    private final FuncionarioService service;

    public FuncionarioController(FuncionarioService service) {
        this.service = service;
    }

    /** Funcionários disponíveis (conta ativa), para selecionar na agenda. Qualquer autenticado. */
    @GetMapping
    public List<FuncionarioResumo> listarDisponiveis(@RequestParam(required = false) @Size(max = 100) String q) {
        return service.listarDisponiveis(q);
    }

    @GetMapping("/{id}")
    @PreAuthorize(ADMIN)
    public FuncionarioResponse buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping
    @PreAuthorize(ADMIN)
    public ResponseEntity<FuncionarioResponse> criar(@Valid @RequestBody FuncionarioRequest request) {
        FuncionarioResponse criado = service.criar(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(criado.id());
        return ResponseEntity.created(location).body(criado);
    }

    @PutMapping("/{id}")
    @PreAuthorize(ADMIN)
    public FuncionarioResponse atualizar(@PathVariable Long id, @Valid @RequestBody FuncionarioAtualizacaoRequest request) {
        return service.atualizar(id, request);
    }
}
