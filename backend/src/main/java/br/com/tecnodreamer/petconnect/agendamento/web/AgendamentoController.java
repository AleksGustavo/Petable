package br.com.tecnodreamer.petconnect.agendamento.web;

import br.com.tecnodreamer.petconnect.agendamento.application.AgendamentoService;
import br.com.tecnodreamer.petconnect.agendamento.application.FiltroAgenda;
import br.com.tecnodreamer.petconnect.agendamento.domain.StatusAgendamento;
import br.com.tecnodreamer.petconnect.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import br.com.tecnodreamer.petconnect.shared.security.UsuarioAtual;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
public class AgendamentoController {

    private static final String PODE_AGENDAR = "hasAnyRole('ADMIN', 'VENDAS')";

    private final AgendamentoService service;

    public AgendamentoController(AgendamentoService service) {
        this.service = service;
    }

    /** Agenda de um período de até 7 dias, em ordem cronológica. */
    @GetMapping("/api/v1/agendamentos")
    public PageResponse<AgendamentoResponse> listar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) StatusAgendamento status,
            @RequestParam(required = false) Long petId,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) Long servicoId,
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        var filtro = new FiltroAgenda(inicio, fim, status, petId, clienteId, servicoId, q);
        return PageResponse.of(service.listar(filtro, PageRequest.of(page, size, Sort.by("dataHora", "id"))));
    }

    @GetMapping("/api/v1/agendamentos/preco")
    public PrecoResponse preco(@RequestParam Long petId, @RequestParam Long servicoId) {
        return service.precoPrevisto(petId, servicoId);
    }

    @GetMapping("/api/v1/agendamentos/{id}")
    public AgendamentoResponse buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping("/api/v1/agendamentos")
    @PreAuthorize(PODE_AGENDAR)
    public ResponseEntity<AgendamentoResponse> criar(@Valid @RequestBody AgendamentoRequest request) {
        AgendamentoResponse criado = service.criar(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(criado.id());
        return ResponseEntity.created(location).body(criado);
    }

    @PutMapping("/api/v1/agendamentos/{id}")
    @PreAuthorize(PODE_AGENDAR)
    public AgendamentoResponse atualizar(@PathVariable Long id, @Valid @RequestBody AgendamentoRequest request) {
        return service.atualizar(id, request);
    }

    /** Única forma de mudar status. O papel exigido depende da transição (conferido no serviço). */
    @PostMapping("/api/v1/agendamentos/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDAS', 'SERVICO')")
    public AgendamentoResponse alterarStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request,
            Authentication auth) {
        Set<String> papeis = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_")).map(a -> a.substring(5)).collect(Collectors.toSet());
        return service.alterarStatus(id, request, papeis, UsuarioAtual.id().orElse(null));
    }

    @GetMapping("/api/v1/pets/{petId}/agendamentos/proximos")
    public List<AgendamentoResponse> proximos(@PathVariable Long petId,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int limit) {
        return service.proximos(petId, limit);
    }
}
