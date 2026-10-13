package br.com.tecnodreamer.petconnect.compra.web;

import br.com.tecnodreamer.petconnect.compra.application.CompraService;
import br.com.tecnodreamer.petconnect.compra.domain.StatusCompra;
import br.com.tecnodreamer.petconnect.shared.security.UsuarioAtual;
import br.com.tecnodreamer.petconnect.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDate;

/** Leitura: ADMIN e VENDAS. Criar, receber e cancelar: só ADMIN. */
@RestController
@RequestMapping("/api/v1/compras")
@PreAuthorize("hasAnyRole('ADMIN', 'VENDAS')")
public class CompraController {

    private static final String ADMIN = "hasRole('ADMIN')";

    private final CompraService service;

    public CompraController(CompraService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<CompraResponse> listar(@RequestParam(required = false) StatusCompra status,
            @RequestParam(required = false) Long fornecedorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var sort = Sort.by(Sort.Direction.DESC, "dataCompra", "id");
        return PageResponse.of(service.listar(status, fornecedorId, dataInicio, dataFim, PageRequest.of(page, size, sort)));
    }

    @GetMapping("/{id}")
    public CompraResponse buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping
    @PreAuthorize(ADMIN)
    public ResponseEntity<CompraResponse> criar(@Valid @RequestBody CompraRequest request) {
        CompraResponse criada = service.criar(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(criada.id());
        return ResponseEntity.created(location).body(criada);
    }

    @PostMapping("/{id}/receber")
    @PreAuthorize(ADMIN)
    public CompraResponse receber(@PathVariable Long id, @Valid @RequestBody RecebimentoRequest request) {
        Long usuarioId = UsuarioAtual.id().orElseThrow(() -> new InsufficientAuthenticationException("Sessão sem usuário"));
        return service.receber(id, request.dataEntrega(), request.dataVencimento(), usuarioId);
    }

    @PostMapping("/{id}/cancelar")
    @PreAuthorize(ADMIN)
    public CompraResponse cancelar(@PathVariable Long id, @Valid @RequestBody CancelamentoCompraRequest request) {
        return service.cancelar(id, request.motivo());
    }
}
