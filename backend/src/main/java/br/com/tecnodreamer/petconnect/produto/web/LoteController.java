package br.com.tecnodreamer.petconnect.produto.web;

import br.com.tecnodreamer.petconnect.produto.application.LoteConsultaService;
import br.com.tecnodreamer.petconnect.produto.domain.StatusLote;
import br.com.tecnodreamer.petconnect.shared.web.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta de lotes, alertas e rastreabilidade (etapa 04). Leitura para ADMIN e VENDAS; as ações (bloquear, perda,
 * regularizar…) ficam em {@link ProdutoController} e são só ADMIN. A API confere o papel; a tela só esconde botões.
 */
@RestController
@Validated
@PreAuthorize("hasAnyRole('ADMIN', 'VENDAS')")
public class LoteController {

    private final LoteConsultaService consulta;

    public LoteController(LoteConsultaService consulta) {
        this.consulta = consulta;
    }

    @GetMapping("/api/v1/lotes")
    public PageResponse<LoteResponse> consultar(@RequestParam(required = false) String busca,
            @RequestParam(required = false) StatusLote status, @RequestParam(required = false) Boolean vencido,
            @RequestParam(required = false) Integer venceEmDias, @RequestParam(required = false) Long produtoId,
            @RequestParam(defaultValue = "true") boolean comSaldo, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.of(consulta.consultar(busca, status, vencido, venceEmDias, produtoId, comSaldo, PageRequest.of(page, size)));
    }

    @GetMapping("/api/v1/lotes/{id}/rastreio")
    public RastreioLoteResponse rastrear(@PathVariable Long id) {
        return consulta.rastrear(id);
    }

    /**
     * Liga/desliga as telas de lote. Qualquer usuário autenticado pode ler (a tela só decide se mostra a aba e o menu);
     * as ações continuam protegidas pelos papéis.
     */
    @GetMapping("/api/v1/estoque/configuracao")
    @PreAuthorize("isAuthenticated()")
    public ConfiguracaoLotesResponse configuracao() {
        return consulta.configuracao();
    }

    @GetMapping("/api/v1/estoque/alertas")
    public AlertasEstoqueResponse alertas() {
        return consulta.alertas();
    }
}
