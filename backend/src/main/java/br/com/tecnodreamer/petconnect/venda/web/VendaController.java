package br.com.tecnodreamer.petconnect.venda.web;

import br.com.tecnodreamer.petconnect.shared.security.UsuarioAtual;
import br.com.tecnodreamer.petconnect.shared.web.PageResponse;
import br.com.tecnodreamer.petconnect.venda.application.RegistroVendaIdempotente;
import br.com.tecnodreamer.petconnect.venda.application.ResultadoVenda;
import br.com.tecnodreamer.petconnect.venda.application.VendaService;
import br.com.tecnodreamer.petconnect.venda.domain.FormaPagamento;
import br.com.tecnodreamer.petconnect.venda.domain.StatusVenda;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDate;

/** ADMIN e VENDAS vendem e consultam; só ADMIN cancela. SERVICO é barrado também por URL. */
@RestController
@RequestMapping("/api/v1/vendas")
@PreAuthorize("hasAnyRole('ADMIN', 'VENDAS')")
public class VendaController {

    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final VendaService service;
    private final RegistroVendaIdempotente registro;

    public VendaController(VendaService service, RegistroVendaIdempotente registro) {
        this.service = service;
        this.registro = registro;
    }

    @GetMapping
    public PageResponse<VendaResponse> listar(@RequestParam(required = false) StatusVenda status,
            @RequestParam(required = false) FormaPagamento formaPagamento,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var sort = Sort.by(Sort.Direction.DESC, "dataVenda", "id");
        return PageResponse.of(service.listar(status, formaPagamento, clienteId, dataInicio, dataFim,
                PageRequest.of(page, size, sort)));
    }

    @GetMapping("/resumo-dia")
    public ResumoDiaResponse resumoDia(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return service.resumoDia(data);
    }

    @GetMapping("/{id}")
    public VendaResponse buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping("/previa")
    public PreviaVendaResponse previa(@Valid @RequestBody VendaRequest request) {
        return service.previa(request, usuarioId());
    }

    @PostMapping
    public ResponseEntity<VendaResponse> registrar(@RequestHeader(value = IDEMPOTENCY_KEY, required = false) String chave,
            @Valid @RequestBody VendaRequest request) {
        ResultadoVenda r = registro.registrar(request, chave, usuarioId());
        if (r.repetida()) {
            return ResponseEntity.ok().header("Idempotent-Replayed", "true").body(r.venda());
        }
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(r.venda().id());
        return ResponseEntity.created(location).body(r.venda());
    }

    @PostMapping("/{id}/cancelar")
    @PreAuthorize("hasRole('ADMIN')")
    public VendaResponse cancelar(@PathVariable Long id, @Valid @RequestBody CancelamentoVendaRequest request) {
        return service.cancelar(id, request.motivo(), usuarioId());
    }

    private static Long usuarioId() {
        return UsuarioAtual.id().orElseThrow(() -> new InsufficientAuthenticationException("Sessão sem usuário"));
    }
}
