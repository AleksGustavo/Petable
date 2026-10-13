package br.com.tecnodreamer.petconnect.financeiro.web;

import br.com.tecnodreamer.petconnect.financeiro.application.CaixaService;
import br.com.tecnodreamer.petconnect.financeiro.domain.StatusCaixa;
import br.com.tecnodreamer.petconnect.shared.security.UsuarioAtual;
import br.com.tecnodreamer.petconnect.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Cada usuário opera o PRÓPRIO caixa; listar todos só ADMIN. */
@RestController
@RequestMapping("/api/v1/caixas")
@PreAuthorize("hasAnyRole('ADMIN', 'VENDAS', 'SERVICO')")
public class CaixaController {

    private final CaixaService service;

    public CaixaController(CaixaService service) {
        this.service = service;
    }

    @PostMapping("/abrir")
    @ResponseStatus(HttpStatus.CREATED)
    public CaixaResponse abrir(@Valid @RequestBody CaixaRequests.Abertura request) {
        return service.abrir(usuarioId(), request.fundoAbertura());
    }

    @GetMapping("/atual")
    public CaixaResponse atual() {
        return service.atual(usuarioId());
    }

    @GetMapping("/resumo-atual")
    public ResumoCaixaAtualResponse resumoAtual() {
        return service.resumoAtual(usuarioId());
    }

    @PostMapping("/atual/suprimentos")
    @ResponseStatus(HttpStatus.CREATED)
    public CaixaResponse.Movimento suprimento(@Valid @RequestBody CaixaRequests.Movimento request) {
        return service.suprimento(usuarioId(), request.valor(), request.motivo());
    }

    @PostMapping("/atual/sangrias")
    @ResponseStatus(HttpStatus.CREATED)
    public CaixaResponse.Movimento sangria(@Valid @RequestBody CaixaRequests.Movimento request) {
        return service.sangria(usuarioId(), request.valor(), request.motivo());
    }

    @GetMapping("/atual/previa-fechamento")
    public PreviaFechamentoResponse previaFechamento() {
        return service.previaFechamento(usuarioId());
    }

    @PostMapping("/atual/fechar")
    public CaixaResponse fechar(@Valid @RequestBody CaixaRequests.Fechamento request) {
        return service.fechar(usuarioId(), request.contado(), request.motivoDiferenca());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<CaixaResponse> listar(@RequestParam(required = false) StatusCaixa status,
            @RequestParam(required = false) Long usuarioId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var sort = Sort.by(Sort.Direction.DESC, "abertoEm", "id");
        return PageResponse.of(service.listar(status, usuarioId, dataInicio, dataFim, PageRequest.of(page, size, sort)));
    }

    @GetMapping("/{id}")
    public CaixaResponse detalhe(@PathVariable Long id, Authentication auth) {
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return service.detalhe(id, usuarioId(), admin);
    }

    private static Long usuarioId() {
        return UsuarioAtual.id().orElseThrow(() -> new InsufficientAuthenticationException("Sessão sem usuário"));
    }
}
