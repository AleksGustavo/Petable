package br.com.tecnodreamer.petconnect.financeiro.web;

import br.com.tecnodreamer.petconnect.financeiro.application.FinanceiroService;
import br.com.tecnodreamer.petconnect.financeiro.application.LancamentoManualIdempotente;
import br.com.tecnodreamer.petconnect.financeiro.application.ResultadoLancamento;
import br.com.tecnodreamer.petconnect.financeiro.domain.CategoriaLancamento;
import br.com.tecnodreamer.petconnect.financeiro.domain.StatusConta;
import br.com.tecnodreamer.petconnect.financeiro.domain.TipoLancamento;
import br.com.tecnodreamer.petconnect.shared.security.UsuarioAtual;
import br.com.tecnodreamer.petconnect.shared.web.PageResponse;
import br.com.tecnodreamer.petconnect.venda.domain.FormaPagamento;
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

/** Financeiro geral: só ADMIN (também barrado por URL no SecurityConfig). */
@RestController
@RequestMapping("/api/v1/financeiro")
@PreAuthorize("hasRole('ADMIN')")
public class FinanceiroController {

    private final FinanceiroService service;
    private final LancamentoManualIdempotente manual;

    public FinanceiroController(FinanceiroService service, LancamentoManualIdempotente manual) {
        this.service = service;
        this.manual = manual;
    }

    @GetMapping("/lancamentos")
    public PageResponse<LancamentoResponse> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) TipoLancamento tipo,
            @RequestParam(required = false) CategoriaLancamento categoria,
            @RequestParam(required = false) FormaPagamento formaPagamento,
            @RequestParam(required = false) String origem,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var sort = Sort.by(Sort.Direction.DESC, "dataMovimentacao", "id");
        return PageResponse.of(service.listar(dataInicio, dataFim, tipo, categoria, formaPagamento, origem,
                PageRequest.of(page, size, sort)));
    }

    @GetMapping("/lancamentos/{id}")
    public LancamentoResponse detalhe(@PathVariable Long id) {
        return service.detalhe(id);
    }

    @PostMapping("/lancamentos")
    public ResponseEntity<LancamentoResponse> lancar(@RequestHeader(value = "Idempotency-Key", required = false) String chave,
            @Valid @RequestBody LancamentoManualRequest request) {
        ResultadoLancamento r = manual.lancar(request, chave, usuarioId());
        if (r.repetido()) {
            return ResponseEntity.ok().header("Idempotent-Replayed", "true").body(r.lancamento());
        }
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(r.lancamento().id());
        return ResponseEntity.created(location).body(r.lancamento());
    }

    @PostMapping("/lancamentos/{id}/estornar")
    public LancamentoResponse estornar(@PathVariable Long id, @Valid @RequestBody MotivoRequest request) {
        return service.estornar(id, request.motivo(), usuarioId());
    }

    @GetMapping("/fluxo")
    public FluxoResponse fluxo(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim) {
        return service.fluxo(dataInicio, dataFim);
    }

    @GetMapping("/resumo-dia")
    public ResumoFinanceiroDiaResponse resumoDia(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return service.resumoDia(data);
    }

    @GetMapping("/contas-a-pagar")
    public PageResponse<ContaPagarResponse> contas(@RequestParam(required = false) StatusConta status,
            @RequestParam(required = false) String situacao,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var sort = Sort.by(Sort.Direction.ASC, "dataVencimento", "id");
        return PageResponse.of(service.contas(status, situacao, dataInicio, dataFim, PageRequest.of(page, size, sort)));
    }

    @GetMapping("/contas-a-pagar/{id}")
    public ContaPagarResponse conta(@PathVariable Long id) {
        return service.conta(id);
    }

    @PostMapping("/contas-a-pagar/{id}/pagar")
    public ContaPagarResponse pagar(@PathVariable Long id, @Valid @RequestBody PagamentoContaRequest request) {
        return service.pagar(id, request.formaPagamento(), usuarioId());
    }

    private static Long usuarioId() {
        return UsuarioAtual.id().orElseThrow(() -> new InsufficientAuthenticationException("Sessão sem usuário"));
    }
}
