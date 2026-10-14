package br.com.tecnodreamer.petconnect.pet.application;

import br.com.tecnodreamer.petconnect.pet.domain.Vacina;
import br.com.tecnodreamer.petconnect.pet.infrastructure.VacinaRepository;
import br.com.tecnodreamer.petconnect.pet.web.VacinaEstoqueResponse;
import br.com.tecnodreamer.petconnect.produto.application.LoteEstoqueService;
import br.com.tecnodreamer.petconnect.produto.domain.Produto;
import br.com.tecnodreamer.petconnect.produto.infrastructure.ProdutoRepository;
import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.error.ConflictException;
import br.com.tecnodreamer.petconnect.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Liga cada vacina do catálogo ao produto de estoque (lotes, etapa 04, item 7, opção A). Com o vínculo e o controle de lote do
 * produto, aplicar a vacina tira uma dose do lote que vence primeiro. Só o ADMIN altera; a API confere o papel.
 */
@Service
public class VacinaEstoqueService {

    private final VacinaRepository vacinas;
    private final ProdutoRepository produtos;
    private final LoteEstoqueService loteService;
    private final AuditoriaService auditoria;

    public VacinaEstoqueService(VacinaRepository vacinas, ProdutoRepository produtos, LoteEstoqueService loteService,
            AuditoriaService auditoria) {
        this.vacinas = vacinas;
        this.produtos = produtos;
        this.loteService = loteService;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public List<VacinaEstoqueResponse> listar() {
        return vacinas.findByAtivoTrueOrderByNomeAscIdAsc().stream().map(VacinaEstoqueResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public List<VacinaEstoqueResponse> doProduto(Long produtoId) {
        return vacinas.findByProdutoIdOrderByNomeAscIdAsc(produtoId).stream().map(VacinaEstoqueResponse::of).toList();
    }

    @Transactional
    public VacinaEstoqueResponse vincular(Long vacinaId, Long produtoId) {
        if (!loteService.habilitado()) {
            throw new ConflictException("O controle de lote não está habilitado nesta instalação.");
        }
        Vacina vacina = vacinas.findById(vacinaId).orElseThrow(() -> new NotFoundException("Vacina", vacinaId));
        Produto produto = produtos.findById(produtoId).orElseThrow(() -> new NotFoundException("Produto", produtoId));
        if (!produto.isAtivo()) {
            throw new ConflictException("O produto está inativo.");
        }
        if (!produto.isControlaLote()) {
            throw new ConflictException("Ligue o controle de lote do produto " + produto.getNome() + " antes de vinculá-lo a uma vacina.");
        }
        vacina.vincularProduto(produto);
        auditoria.registrar("VACINA_PRODUTO_VINCULADO", "vacina", vacinaId, detalhes(vacinaId, produtoId));
        return VacinaEstoqueResponse.of(vacina);
    }

    @Transactional
    public void desvincular(Long vacinaId) {
        Vacina vacina = vacinas.findById(vacinaId).orElseThrow(() -> new NotFoundException("Vacina", vacinaId));
        Long anterior = vacina.getProduto() == null ? null : vacina.getProduto().getId();
        vacina.vincularProduto(null);
        auditoria.registrar("VACINA_PRODUTO_DESVINCULADO", "vacina", vacinaId, detalhes(vacinaId, anterior));
    }

    private static Map<String, Object> detalhes(Long vacinaId, Long produtoId) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("vacinaId", vacinaId);
        d.put("produtoId", produtoId);
        return d;
    }
}
