package br.com.tecnodreamer.petconnect.produto.application;

import br.com.tecnodreamer.petconnect.compra.infrastructure.ItemCompraLoteRepository;
import br.com.tecnodreamer.petconnect.pet.web.CatalogoItem;
import br.com.tecnodreamer.petconnect.produto.domain.LoteEstoque;
import br.com.tecnodreamer.petconnect.produto.domain.StatusLote;
import br.com.tecnodreamer.petconnect.produto.infrastructure.LoteEstoqueRepository;
import br.com.tecnodreamer.petconnect.produto.infrastructure.MovimentacaoEstoqueRepository;
import br.com.tecnodreamer.petconnect.produto.infrastructure.ProdutoRepository;
import br.com.tecnodreamer.petconnect.produto.web.AlertasEstoqueResponse;
import br.com.tecnodreamer.petconnect.produto.web.ConfiguracaoLotesResponse;
import br.com.tecnodreamer.petconnect.produto.web.LoteResponse;
import br.com.tecnodreamer.petconnect.produto.web.RastreioLoteResponse;
import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;
import br.com.tecnodreamer.petconnect.shared.error.NotFoundException;
import br.com.tecnodreamer.petconnect.venda.infrastructure.ItemVendaLoteRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Leituras de lote para a tela de estoque: consulta paginada, alertas e rastreabilidade. Só lê; os alertas são calculados na
 * hora a partir dos lotes reais (não existe tabela de alerta, então nada dispara duas vezes).
 */
@Service
public class LoteConsultaService {

    static final int LIMITE_MOVIMENTOS_NO_RASTREIO = 500;

    private final LoteEstoqueRepository lotes;
    private final ProdutoRepository produtos;
    private final MovimentacaoEstoqueRepository movimentacoes;
    private final ItemCompraLoteRepository itensCompraLote;
    private final ItemVendaLoteRepository itensVendaLote;
    private final LoteEstoqueService loteService;
    private final LotesProperties propriedades;

    public LoteConsultaService(LoteEstoqueRepository lotes, ProdutoRepository produtos, MovimentacaoEstoqueRepository movimentacoes,
            ItemCompraLoteRepository itensCompraLote, ItemVendaLoteRepository itensVendaLote, LoteEstoqueService loteService,
            LotesProperties propriedades) {
        this.lotes = lotes;
        this.produtos = produtos;
        this.movimentacoes = movimentacoes;
        this.itensCompraLote = itensCompraLote;
        this.itensVendaLote = itensVendaLote;
        this.loteService = loteService;
        this.propriedades = propriedades;
    }

    /**
     * @param busca      nome do produto, código do lote (parte do texto) ou código de barras (início)
     * @param vencido    true = só lotes já vencidos
     * @param venceEmDias lotes que vencem de hoje até hoje + N dias (não combina com {@code vencido})
     */
    @Transactional(readOnly = true)
    public Page<LoteResponse> consultar(String busca, StatusLote status, Boolean vencido, Integer venceEmDias, Long produtoId,
            boolean comSaldo, Pageable pageable) {
        if (Boolean.TRUE.equals(vencido) && venceEmDias != null) {
            throw new CampoInvalidoException("venceEmDias", "Use vencidos ou a janela de vencimento, não os dois.");
        }
        if (venceEmDias != null && (venceEmDias < 0 || venceEmDias > 3650)) {
            throw new CampoInvalidoException("venceEmDias", "A janela deve ficar entre 0 e 3650 dias.");
        }
        LocalDate hoje = loteService.hoje();
        LocalDate de = null;
        LocalDate ate = null;
        if (Boolean.TRUE.equals(vencido)) {
            ate = hoje.minusDays(1);
        } else if (venceEmDias != null) {
            de = hoje;
            ate = hoje.plusDays(venceEmDias);
        }
        String termo = busca == null || busca.isBlank() ? null : busca.trim().toLowerCase(Locale.ROOT);
        String curinga = termo == null ? null : "%" + termo + "%";
        String inicio = termo == null ? null : termo + "%";
        return lotes.consultar(produtoId, curinga, inicio, status, comSaldo, de, ate, pageable)
                .map(l -> LoteResponse.of(l, hoje, loteService.margemDeBloqueio(l.getProduto())));
    }

    public ConfiguracaoLotesResponse configuracao() {
        return new ConfiguracaoLotesResponse(propriedades.habilitado(), propriedades.faixasAlertaDias());
    }

    @Transactional(readOnly = true)
    public AlertasEstoqueResponse alertas() {
        LocalDate hoje = loteService.hoje();
        List<AlertasEstoqueResponse.Faixa> faixas = new ArrayList<>();
        int anterior = -1;
        for (int ate : propriedades.faixasAlertaDias()) {
            int de = anterior + 1;
            var t = lotes.totaisPorValidade(hoje.plusDays(de), hoje.plusDays(ate));
            faixas.add(new AlertasEstoqueResponse.Faixa(de, ate, t.getLotes(), t.getUnidades()));
            anterior = ate;
        }
        var vencidos = lotes.totaisPorValidade(null, hoje.minusDays(1));
        var bloqueados = lotes.totaisBloqueados();
        return new AlertasEstoqueResponse(propriedades.habilitado(), hoje, faixas,
                new AlertasEstoqueResponse.Grupo(vencidos.getLotes(), vencidos.getUnidades()),
                new AlertasEstoqueResponse.Grupo(bloqueados.getLotes(), bloqueados.getUnidades()),
                propriedades.habilitado() ? produtos.contarComSaldoSemControleDeLote() : 0, produtos.contarAbaixoDoMinimo());
    }

    @Transactional(readOnly = true)
    public RastreioLoteResponse rastrear(Long loteId) {
        LoteEstoque lote = lotes.findById(loteId).orElseThrow(() -> new NotFoundException("Lote", loteId));
        LocalDate hoje = loteService.hoje();
        var entradas = itensCompraLote.rastreio(loteId).stream().map(i -> {
            var compra = i.getItemCompra().getCompra();
            var fornecedor = compra.getFornecedor();
            return new RastreioLoteResponse.Entrada(compra.getId(), new CatalogoItem(fornecedor.getId(), fornecedor.getNomeFantasia()),
                    compra.getDataCompra(), compra.getDataEntrega(), i.getQuantidade(), i.getItemCompra().getPrecoCustoUnitario());
        }).toList();
        var saidas = itensVendaLote.rastreio(loteId).stream().map(a -> {
            var venda = a.getItemVenda().getVenda();
            return new RastreioLoteResponse.Saida(venda.getId(), venda.getDataVenda(), venda.getStatus(), a.getQuantidade());
        }).toList();
        var movimentos = movimentacoes.findByLoteIdOrderByIdAsc(loteId, PageRequest.of(0, LIMITE_MOVIMENTOS_NO_RASTREIO)).stream()
                .map(m -> new RastreioLoteResponse.Movimento(m.getId(), m.getTipo(), m.getQuantidade(), m.getLoteSaldoAnterior(),
                        m.getLoteSaldoResultante(), m.getCompra() == null ? null : m.getCompra().getId(), m.getVendaId(),
                        m.getMotivo(), m.getOperacaoId(), new CatalogoItem(m.getUsuario().getId(), m.getUsuario().getUsername()),
                        m.getCreatedAt()))
                .toList();
        return new RastreioLoteResponse(LoteResponse.of(lote, hoje, loteService.margemDeBloqueio(lote.getProduto())), entradas, saidas,
                movimentos, movimentacoes.countByLoteId(loteId));
    }
}
