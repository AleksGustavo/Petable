package br.com.tecnodreamer.petconnect.produto.application;

import br.com.tecnodreamer.petconnect.fornecedor.domain.Fornecedor;
import br.com.tecnodreamer.petconnect.fornecedor.infrastructure.FornecedorRepository;
import br.com.tecnodreamer.petconnect.produto.domain.CategoriaProduto;
import br.com.tecnodreamer.petconnect.produto.domain.Produto;
import br.com.tecnodreamer.petconnect.produto.infrastructure.CategoriaProdutoRepository;
import br.com.tecnodreamer.petconnect.produto.infrastructure.ProdutoRepository;
import br.com.tecnodreamer.petconnect.produto.web.ProdutoAtualizacaoRequest;
import br.com.tecnodreamer.petconnect.produto.web.ProdutoRapidoResponse;
import br.com.tecnodreamer.petconnect.produto.web.ProdutoRequest;
import br.com.tecnodreamer.petconnect.produto.web.ProdutoResponse;
import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;
import br.com.tecnodreamer.petconnect.shared.error.ConflictException;
import br.com.tecnodreamer.petconnect.shared.error.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProdutoService {

    static final String ENTIDADE = "produto";
    static final int LIMITE_BUSCA_RAPIDA = 50;

    private final ProdutoRepository produtos;
    private final CategoriaProdutoRepository categorias;
    private final FornecedorRepository fornecedores;
    private final AuditoriaService auditoria;

    public ProdutoService(ProdutoRepository produtos, CategoriaProdutoRepository categorias,
            FornecedorRepository fornecedores, AuditoriaService auditoria) {
        this.produtos = produtos;
        this.categorias = categorias;
        this.fornecedores = fornecedores;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public List<CategoriaResponse> categorias() {
        return categorias.findAllByOrderByNomeAsc().stream()
                .map(c -> new CategoriaResponse(c.getId(), c.getNome(), c.getDescricao())).toList();
    }

    public record CategoriaResponse(Long id, String nome, String descricao) {
    }

    @Transactional(readOnly = true)
    public Page<ProdutoResponse> listar(String busca, Long categoriaId, Boolean ativo, boolean abaixoMinimo, Pageable pageable) {
        return produtos.buscar(limpo(busca), categoriaId, ativo, abaixoMinimo, pageable).map(ProdutoResponse::of);
    }

    /** Só ativos, até 50, código exato primeiro. Menos de 2 caracteres não busca. */
    @Transactional(readOnly = true)
    public List<ProdutoRapidoResponse> buscaRapida(String busca) {
        String termo = limpo(busca);
        if (termo == null || termo.length() < 2) {
            return List.of();
        }
        return produtos.buscaRapida(termo, PageRequest.of(0, LIMITE_BUSCA_RAPIDA)).stream()
                .map(ProdutoRapidoResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public ProdutoResponse buscar(Long id) {
        return ProdutoResponse.of(carregar(id));
    }

    @Transactional(readOnly = true)
    public long contarAbaixoDoMinimo() {
        return produtos.contarAbaixoDoMinimo();
    }

    @Transactional
    public ProdutoResponse criar(ProdutoRequest req) {
        String codigo = limpo(req.codigoBarras());
        if (codigo != null && produtos.existsByCodigoBarras(codigo)) {
            throw new ConflictException("Já existe um produto com este código de barras.");
        }
        Produto p = new Produto(req.nome().trim(), limpo(req.descricao()), categoria(req.categoriaId()),
                fornecedor(req.fornecedorPadraoId()), codigo, req.precoCusto(), req.precoVenda(), req.estoqueMinimo());
        Produto salvo = produtos.saveAndFlush(p);
        auditoria.registrar("PRODUTO_CRIADO", ENTIDADE, salvo.getId(), Map.of("nome", salvo.getNome()));
        return ProdutoResponse.of(salvo);
    }

    @Transactional
    public ProdutoResponse atualizar(Long id, ProdutoAtualizacaoRequest req) {
        Produto p = carregar(id);
        String codigo = limpo(req.codigoBarras());
        if (codigo != null && produtos.existsByCodigoBarrasAndIdNot(codigo, id)) {
            throw new ConflictException("Já existe outro produto com este código de barras.");
        }
        p.atualizar(req.nome().trim(), limpo(req.descricao()), categoria(req.categoriaId()),
                fornecedor(req.fornecedorPadraoId()), codigo, req.precoVenda(), req.estoqueMinimo());
        Produto salvo = produtos.saveAndFlush(p);
        auditoria.registrar("PRODUTO_ATUALIZADO", ENTIDADE, id, Map.of("nome", salvo.getNome()));
        return ProdutoResponse.of(salvo);
    }

    /** Preserva saldo e histórico (DA-10); só deixa de entrar em compras e no PDV. */
    @Transactional
    public ProdutoResponse desativar(Long id) {
        Produto p = carregar(id);
        if (p.isAtivo()) {
            p.desativar();
            Map<String, Object> detalhes = new HashMap<>();
            detalhes.put("nome", p.getNome());
            detalhes.put("saldo", p.getQuantidadeEstoque());
            auditoria.registrar("PRODUTO_DESATIVADO", ENTIDADE, id, detalhes);
        }
        return ProdutoResponse.of(p);
    }

    @Transactional
    public ProdutoResponse ativar(Long id) {
        Produto p = carregar(id);
        if (!p.isAtivo()) {
            p.ativar();
            auditoria.registrar("PRODUTO_ATIVADO", ENTIDADE, id, Map.of("nome", p.getNome()));
        }
        return ProdutoResponse.of(p);
    }

    private CategoriaProduto categoria(Long id) {
        return categorias.findById(id).orElseThrow(() -> new CampoInvalidoException("categoriaId", "Categoria não encontrada."));
    }

    private Fornecedor fornecedor(Long id) {
        if (id == null) {
            return null;
        }
        return fornecedores.findById(id).filter(Fornecedor::isAtivo)
                .orElseThrow(() -> new CampoInvalidoException("fornecedorPadraoId", "Fornecedor não encontrado ou inativo."));
    }

    private Produto carregar(Long id) {
        return produtos.findWithDetalhesById(id).orElseThrow(() -> new NotFoundException("Produto", id));
    }

    private static String limpo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
