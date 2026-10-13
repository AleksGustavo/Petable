package br.com.tecnodreamer.petconnect.relatorio.web;

import br.com.tecnodreamer.petconnect.relatorio.application.Periodo;
import br.com.tecnodreamer.petconnect.relatorio.application.RelatorioCsv;
import br.com.tecnodreamer.petconnect.relatorio.application.RelatorioService;
import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.error.NotFoundException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Relatórios gerenciais (somente leitura). VENDAS: vendas, produtos mais vendidos e
 * curva ABC; o resto é só ADMIN (também barrado por URL no SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/relatorios")
@PreAuthorize("hasAnyRole('ADMIN', 'VENDAS')")
public class RelatorioController {

    private static final String ADMIN = "hasRole('ADMIN')";

    private final RelatorioService service;
    private final RelatorioCsv csv;
    private final AuditoriaService auditoria;

    public RelatorioController(RelatorioService service, RelatorioCsv csv, AuditoriaService auditoria) {
        this.service = service;
        this.csv = csv;
        this.auditoria = auditoria;
    }

    private static Periodo periodo(LocalDate inicio, LocalDate fim) {
        return Periodo.validar(inicio, fim);
    }

    @GetMapping("/vendas")
    public Relatorios.Vendas vendas(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim) {
        return service.vendas(periodo(dataInicio, dataFim));
    }

    @GetMapping("/produtos-mais-vendidos")
    public Relatorios.MaisVendidos maisVendidos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limite) {
        return service.maisVendidos(periodo(dataInicio, dataFim), limite);
    }

    @GetMapping("/curva-abc")
    public Relatorios.CurvaAbc curvaAbc(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim) {
        return service.curvaAbc(periodo(dataInicio, dataFim));
    }

    @GetMapping("/estoque")
    @PreAuthorize(ADMIN)
    public Relatorios.Estoque estoque(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim) {
        return service.estoque(periodo(dataInicio, dataFim));
    }

    @GetMapping("/margem")
    @PreAuthorize(ADMIN)
    public Relatorios.Margem margem(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim) {
        return service.margem(periodo(dataInicio, dataFim));
    }

    @GetMapping("/servicos")
    @PreAuthorize(ADMIN)
    public Relatorios.Servicos servicos(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim) {
        return service.servicos(periodo(dataInicio, dataFim));
    }

    @GetMapping("/despesas")
    @PreAuthorize(ADMIN)
    public Relatorios.Despesas despesas(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim) {
        return service.despesas(periodo(dataInicio, dataFim));
    }

    @GetMapping("/caixas")
    @PreAuthorize(ADMIN)
    public Relatorios.Caixas caixas(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim) {
        return service.caixas(periodo(dataInicio, dataFim));
    }

    @GetMapping("/contas")
    @PreAuthorize(ADMIN)
    public Relatorios.Contas contas(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim) {
        return service.contas(periodo(dataInicio, dataFim));
    }

    /** CSV de qualquer relatório; a autorização segue a do relatório (DA-09) e a exportação é auditada. */
    @GetMapping("/{tipo}/csv")
    public ResponseEntity<byte[]> csv(@PathVariable String tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limite, Authentication auth) {
        if (!RelatorioCsv.TIPOS.contains(tipo)) {
            throw new NotFoundException("Relatório", tipo);
        }
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!admin && !RelatorioCsv.DE_VENDAS.contains(tipo)) {
            throw new AccessDeniedException("Relatório restrito ao ADMIN");
        }
        Periodo p = periodo(dataInicio, dataFim);
        RelatorioCsv.Arquivo arquivo = csv.gerar(tipo, p, limite);
        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("tipo", tipo);
        detalhes.put("dataInicio", p.inicio().toString());
        detalhes.put("dataFim", p.fim().toString());
        detalhes.put("linhas", arquivo.linhas());
        auditoria.registrar("RELATORIO_EXPORTADO", "relatorio", tipo, detalhes);
        String nome = "relatorio-%s-%s-%s.csv".formatted(tipo, p.inicio(), p.fim());
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nome + "\"")
                .body(arquivo.conteudo());
    }
}
