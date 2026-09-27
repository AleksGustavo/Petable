package br.com.tecnodreamer.petconnect.produto.web;

import br.com.tecnodreamer.petconnect.produto.application.EstoqueService;
import br.com.tecnodreamer.petconnect.produto.application.ProdutoService;
import br.com.tecnodreamer.petconnect.produto.domain.TipoMovimentacao;
import br.com.tecnodreamer.petconnect.shared.security.UsuarioAtual;
import br.com.tecnodreamer.petconnect.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.Map;

@RestController
public class ProdutoController {

    private static final String MANTEM = "hasAnyRole('ADMIN', 'VENDAS')";
    private static final String ADMIN = "hasRole('ADMIN')";

    private final ProdutoService produtos;
    private final EstoqueService estoque;

    public ProdutoController(ProdutoService produtos, EstoqueService estoque) {
        this.produtos = produtos;
        this.estoque = estoque;
    }

    @GetMapping("/api/v1/categorias-produto")
    public List<ProdutoService.CategoriaResponse> categorias() {
        return produtos.categorias();
    }

    @GetMapping("/api/v1/produtos")
    public PageResponse<ProdutoResponse> listar(@RequestParam(required = false) @Size(max = 120) String q,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) Boolean ativo,
            @RequestParam(defaultValue = "false") boolean abaixoMinimo,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.of(produtos.listar(q, categoriaId, ativo, abaixoMinimo, PageRequest.of(page, size, Sort.by("nome", "id"))));
    }

    @GetMapping("/api/v1/produtos/busca-rapida")
    public List<ProdutoRapidoResponse> buscaRapida(@RequestParam(required = false) @Size(max = 120) String q) {
        return produtos.buscaRapida(q);
    }

    @GetMapping("/api/v1/produtos/resumo-estoque")
    public Map<String, Long> resumoEstoque() {
        return Map.of("produtosAbaixoDoMinimo", produtos.contarAbaixoDoMinimo());
    }

    @GetMapping("/api/v1/produtos/{id}")
    public ProdutoResponse buscar(@PathVariable Long id) {
        return produtos.buscar(id);
    }

    @PostMapping("/api/v1/produtos")
    @PreAuthorize(MANTEM)
    public ResponseEntity<ProdutoResponse> criar(@Valid @RequestBody ProdutoRequest request) {
        ProdutoResponse criado = produtos.criar(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(criado.id());
        return ResponseEntity.created(location).body(criado);
    }

    @PutMapping("/api/v1/produtos/{id}")
    @PreAuthorize(MANTEM)
    public ProdutoResponse atualizar(@PathVariable Long id, @Valid @RequestBody ProdutoAtualizacaoRequest request) {
        return produtos.atualizar(id, request);
    }

    @PatchMapping("/api/v1/produtos/{id}/desativar")
    @PreAuthorize(MANTEM)
    public ProdutoResponse desativar(@PathVariable Long id) {
        return produtos.desativar(id);
    }

    @PatchMapping("/api/v1/produtos/{id}/ativar")
    @PreAuthorize(ADMIN)
    public ProdutoResponse ativar(@PathVariable Long id) {
        return produtos.ativar(id);
    }

    @GetMapping("/api/v1/produtos/{id}/movimentacoes")
    public PageResponse<MovimentacaoResponse> movimentacoes(@PathVariable Long id,
            @RequestParam(required = false) TipoMovimentacao tipo,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.of(estoque.historico(id, tipo, PageRequest.of(page, size)));
    }

    @PostMapping("/api/v1/produtos/{id}/ajustes-estoque")
    @PreAuthorize(ADMIN)
    @ResponseStatus(HttpStatus.CREATED)
    public MovimentacaoResponse ajustar(@PathVariable Long id, @Valid @RequestBody AjusteEstoqueRequest request) {
        return estoque.ajustar(id, request.quantidade(), request.motivo(), usuarioAtual());
    }

    static Long usuarioAtual() {
        return UsuarioAtual.id().orElseThrow(() -> new InsufficientAuthenticationException("Sessão sem usuário"));
    }
}
