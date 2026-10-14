package br.com.tecnodreamer.petconnect.produto;

import br.com.tecnodreamer.petconnect.IntegrationTest;
import br.com.tecnodreamer.petconnect.compra.domain.ItemCompra;
import br.com.tecnodreamer.petconnect.compra.domain.ItemCompraLote;
import br.com.tecnodreamer.petconnect.compra.infrastructure.ItemCompraLoteRepository;
import br.com.tecnodreamer.petconnect.produto.application.LoteEstoqueService;
import br.com.tecnodreamer.petconnect.produto.application.LotesProperties;
import br.com.tecnodreamer.petconnect.produto.domain.LoteEstoque;
import br.com.tecnodreamer.petconnect.produto.domain.MovimentacaoEstoque;
import br.com.tecnodreamer.petconnect.produto.domain.Produto;
import br.com.tecnodreamer.petconnect.produto.domain.StatusLote;
import br.com.tecnodreamer.petconnect.produto.domain.TipoMovimentacao;
import br.com.tecnodreamer.petconnect.produto.infrastructure.CategoriaProdutoRepository;
import br.com.tecnodreamer.petconnect.produto.infrastructure.LoteEstoqueRepository;
import br.com.tecnodreamer.petconnect.produto.infrastructure.MovimentacaoEstoqueRepository;
import br.com.tecnodreamer.petconnect.produto.infrastructure.ProdutoRepository;
import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;
import br.com.tecnodreamer.petconnect.shared.error.ConflictException;
import br.com.tecnodreamer.petconnect.usuario.domain.Usuario;
import br.com.tecnodreamer.petconnect.usuario.infrastructure.UsuarioRepository;
import br.com.tecnodreamer.petconnect.venda.domain.ItemVenda;
import br.com.tecnodreamer.petconnect.venda.domain.ItemVendaLote;
import br.com.tecnodreamer.petconnect.venda.infrastructure.ItemVendaLoteRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Etapa 02 de lotes: persistência, constraints, índices e rastreio do núcleo de lote. Nada aqui passa por
 * rota HTTP; as movimentações de saldo são montadas à mão porque quem as faz de verdade é a etapa 03.
 */
class LoteEstoqueIT extends IntegrationTest {

    @Autowired
    private LoteEstoqueRepository lotes;
    @Autowired
    private LoteEstoqueService servico;
    @Autowired
    private LotesProperties propriedades;
    @Autowired
    private ProdutoRepository produtos;
    @Autowired
    private CategoriaProdutoRepository categorias;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private MovimentacaoEstoqueRepository movimentacoes;
    @Autowired
    private ItemVendaLoteRepository itensVendaLote;
    @Autowired
    private ItemCompraLoteRepository itensCompraLote;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager em;
    @Autowired
    private PlatformTransactionManager tm;

    private TransactionTemplate tx;
    private long adminId;

    @BeforeEach
    void preparar() {
        tx = new TransactionTemplate(tm);
        adminId = jdbc.queryForObject("select id from usuario where username = ?", Long.class, ADMIN_USER);
    }

    // ------------------------------------------------------------------ padrões

    @Test
    void margemGlobalDeBloqueioNasceZero() {
        assertThat(propriedades.diasBloqueioAntesValidade()).isZero();
    }

    @Test
    void produtoNovoNaoControlaLoteEExigeValidadeSeForLigado() {
        long id = novoProduto(false);
        var linha = jdbc.queryForMap(
                "select controla_lote, permite_sem_validade, dias_bloqueio_validade from produto where id = ?", id);
        assertThat(linha.get("controla_lote")).isIn(false, 0);
        assertThat(linha.get("permite_sem_validade")).isIn(false, 0);
        assertThat(linha.get("dias_bloqueio_validade")).isNull();
    }

    // ---------------------------------------------------------------------- CRUD

    @Test
    void criaLoteEMovimentaComRastroNoLivroRazao() {
        long produtoId = novoProduto(true);
        LocalDate validade = LocalDate.now().plusDays(200);
        long loteId = tx.execute(s -> {
            Produto p = produtos.findById(produtoId).orElseThrow();
            Usuario u = usuarios.findById(adminId).orElseThrow();
            LoteEstoque lote = servico.obterOuCriar(p, "  L-100 ", validade, new BigDecimal("12.50"), LocalDate.now(), null, null, u);
            movimentarEntrada(p, lote, 10, u);
            return lote.getId();
        });

        LoteEstoque lido = lotes.findById(loteId).orElseThrow();
        assertThat(lido.getCodigoLote()).isEqualTo("L-100");
        assertThat(lido.getValidade()).isEqualTo(validade);
        assertThat(lido.getSaldo()).isEqualTo(10);
        assertThat(lido.getStatus()).isEqualTo(StatusLote.DISPONIVEL);
        assertThat(lido.getCustoUnitario()).isEqualByComparingTo("12.50");
        assertThat(lido.getCreatedAt()).isNotNull();
        assertThat(saldoProduto(produtoId)).isEqualTo(10);

        // Atualização do saldo e do status persiste; código, validade e custo seguem imutáveis.
        tx.executeWithoutResult(s -> {
            LoteEstoque l = lotes.findById(loteId).orElseThrow();
            l.bloquear();
            l.movimentar(-4);
            Produto p = produtos.findById(produtoId).orElseThrow();
            p.movimentar(-4);
            Usuario u = usuarios.findById(adminId).orElseThrow();
            movimentacoes.save(new MovimentacaoEstoque(p, TipoMovimentacao.PERDA, -4, 10, 6, null, null, u, "dano", l, 10, UUID.randomUUID().toString()));
        });
        LoteEstoque depois = lotes.findById(loteId).orElseThrow();
        assertThat(depois.getStatus()).isEqualTo(StatusLote.BLOQUEADO);
        assertThat(depois.getSaldo()).isEqualTo(6);
        assertThat(depois.getCodigoLote()).isEqualTo("L-100");

        assertThat(lotes.divergenciasDeSaldoDoLote()).doesNotContain(loteId);
        assertThat(lotes.divergenciasDoProdutoControlado()).doesNotContain(produtoId);
        assertThat(lotes.somaSaldos(produtoId)).isEqualTo(6);
        // I1: total do produto = soma dos movimentos (fora REGULARIZACAO_LOTE).
        assertThat(jdbc.queryForObject("select sum(quantidade) from movimentacao_estoque where produto_id = ? and tipo <> 'REGULARIZACAO_LOTE'",
                Integer.class, produtoId)).isEqualTo(6);
    }

    @Test
    void divergenciaDeSaldoEhDetectadaPelasConsultasDeReconciliacao() {
        long produtoId = novoProduto(true);
        long loteId = tx.execute(s -> {
            Produto p = produtos.findById(produtoId).orElseThrow();
            Usuario u = usuarios.findById(adminId).orElseThrow();
            LoteEstoque lote = servico.obterOuCriar(p, codigo(), LocalDate.now().plusDays(90), null, LocalDate.now(), null, null, u);
            movimentarEntrada(p, lote, 5, u);
            return lote.getId();
        });
        // Adultera o saldo por fora do livro-razão (o que um bug ou um UPDATE manual faria).
        jdbc.update("update lote_estoque set saldo = 9 where id = ?", loteId);
        assertThat(lotes.divergenciasDeSaldoDoLote()).contains(loteId);
        assertThat(lotes.divergenciasDoProdutoControlado()).contains(produtoId);
        jdbc.update("update lote_estoque set saldo = 5 where id = ?", loteId);
        assertThat(lotes.divergenciasDeSaldoDoLote()).doesNotContain(loteId);
    }

    // ------------------------------------------------- identidade e política (D3, D5)

    @Test
    void mesmoCodigoEMesmaValidadeReaproveitaOLoteSemSobrescreverCusto() {
        long produtoId = novoProduto(true);
        LocalDate validade = LocalDate.now().plusDays(120);
        String codigo = codigo();
        long primeiro = tx.execute(s -> servico.obterOuCriar(produtos.findById(produtoId).orElseThrow(), codigo, validade,
                new BigDecimal("10.00"), LocalDate.now(), null, null, usuarios.findById(adminId).orElseThrow()).getId());
        long segundo = tx.execute(s -> servico.obterOuCriar(produtos.findById(produtoId).orElseThrow(), codigo, validade,
                new BigDecimal("99.00"), LocalDate.now().plusDays(3), null, null, usuarios.findById(adminId).orElseThrow()).getId());

        assertThat(segundo).isEqualTo(primeiro);
        LoteEstoque lote = lotes.findById(primeiro).orElseThrow();
        assertThat(lote.getCustoUnitario()).isEqualByComparingTo("10.00");
        assertThat(lote.getDataRecebimento()).isEqualTo(LocalDate.now());
        assertThat(lotes.findByProdutoIdOrderByIdAsc(produtoId)).hasSize(1);
    }

    @Test
    void mesmoCodigoComOutraValidadeEhConflito() {
        long produtoId = novoProduto(true);
        String codigo = codigo();
        tx.executeWithoutResult(s -> servico.obterOuCriar(produtos.findById(produtoId).orElseThrow(), codigo,
                LocalDate.now().plusDays(100), null, LocalDate.now(), null, null, usuarios.findById(adminId).orElseThrow()));
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> servico.obterOuCriar(produtos.findById(produtoId).orElseThrow(), codigo,
                LocalDate.now().plusDays(101), null, LocalDate.now(), null, null, usuarios.findById(adminId).orElseThrow())))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void mesmoCodigoEmProdutosDiferentesSaoLotesDiferentes() {
        long a = novoProduto(true);
        long b = novoProduto(true);
        String codigo = codigo();
        long idA = tx.execute(s -> servico.obterOuCriar(produtos.findById(a).orElseThrow(), codigo, LocalDate.now().plusDays(50),
                null, LocalDate.now(), null, null, usuarios.findById(adminId).orElseThrow()).getId());
        long idB = tx.execute(s -> servico.obterOuCriar(produtos.findById(b).orElseThrow(), codigo, LocalDate.now().plusDays(50),
                null, LocalDate.now(), null, null, usuarios.findById(adminId).orElseThrow()).getId());
        assertThat(idA).isNotEqualTo(idB);
    }

    @Test
    void validadeObrigatoriaSalvoPoliticaExplicitaDoProduto() {
        long exige = novoProduto(true);
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> servico.obterOuCriar(produtos.findById(exige).orElseThrow(), codigo(),
                null, null, LocalDate.now(), null, null, usuarios.findById(adminId).orElseThrow())))
                .isInstanceOf(CampoInvalidoException.class);

        long permite = novoProduto(true);
        tx.executeWithoutResult(s -> produtos.findById(permite).orElseThrow().definirPoliticaDeLote(true, null));
        Long loteId = tx.execute(s -> servico.obterOuCriar(produtos.findById(permite).orElseThrow(), codigo(),
                null, null, LocalDate.now(), null, null, usuarios.findById(adminId).orElseThrow()).getId());
        assertThat(lotes.findById(loteId).orElseThrow().getValidade()).isNull();
    }

    @Test
    void codigoVazioOuLongoDemaisEhRejeitado() {
        long id = novoProduto(true);
        for (String ruim : new String[] {"   ", "", "x".repeat(51)}) {
            assertThatThrownBy(() -> tx.executeWithoutResult(s -> servico.obterOuCriar(produtos.findById(id).orElseThrow(), ruim,
                    LocalDate.now().plusDays(10), null, LocalDate.now(), null, null, usuarios.findById(adminId).orElseThrow())))
                    .isInstanceOf(CampoInvalidoException.class);
        }
    }

    @Test
    void servicoExigeTransacaoAberta() {
        long id = novoProduto(true);
        Produto p = produtos.findById(id).orElseThrow();
        Usuario u = usuarios.findById(adminId).orElseThrow();
        assertThatThrownBy(() -> servico.obterOuCriar(p, codigo(), LocalDate.now().plusDays(10), null, LocalDate.now(), null, null, u))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    // ------------------------------------------------------------------- FEFO (D6)

    @Test
    void fefoOrdenaPorValidadeComNuloPorUltimoEExcluiVencidoBloqueadoEZerado() {
        long produtoId = novoProduto(true);
        tx.executeWithoutResult(s -> produtos.findById(produtoId).orElseThrow().definirPoliticaDeLote(true, null));
        LocalDate hoje = servico.hoje();
        long tarde = lote(produtoId, "TARDE", hoje.plusDays(300), 5);
        long cedo = lote(produtoId, "CEDO", hoje.plusDays(30), 5);
        long semValidade = lote(produtoId, "SEM", null, 5);
        long hojeIgual = lote(produtoId, "HOJE", hoje, 5);
        lote(produtoId, "VENCIDO", hoje.minusDays(1), 5);
        lote(produtoId, "ZERADO", hoje.plusDays(10), 0);
        long bloqueado = lote(produtoId, "BLOQ", hoje.plusDays(5), 5);
        tx.executeWithoutResult(s -> lotes.findById(bloqueado).orElseThrow().bloquear());

        List<Long> ordem = tx.execute(s -> servico.vendaveis(produtos.findById(produtoId).orElseThrow()).stream().map(LoteEstoque::getId).toList());
        // Vendável até a data inclusive: o lote que vence hoje ainda sai, e primeiro.
        assertThat(ordem).containsExactly(hojeIgual, cedo, tarde, semValidade);
    }

    @Test
    void empateDeValidadeDesempataPorIdEstavel() {
        long produtoId = novoProduto(true);
        LocalDate v = servico.hoje().plusDays(60);
        long primeiro = lote(produtoId, "A1", v, 3);
        long segundo = lote(produtoId, "A2", v, 3);
        List<Long> ordem = tx.execute(s -> servico.vendaveis(produtos.findById(produtoId).orElseThrow()).stream().map(LoteEstoque::getId).toList());
        assertThat(ordem).containsExactly(primeiro, segundo);
    }

    @Test
    void margemDeBloqueioDoProdutoSobrescreveAGlobal() {
        long produtoId = novoProduto(true);
        LocalDate hoje = servico.hoje();
        long perto = lote(produtoId, "PERTO", hoje.plusDays(10), 5);
        long longe = lote(produtoId, "LONGE", hoje.plusDays(100), 5);
        // Sem margem própria: usa a global (0), então os dois saem.
        assertThat(ids(produtoId)).containsExactly(perto, longe);
        // Margem de 30 dias: o lote que vence em 10 dias deixa de ser vendável, mas continua existindo.
        tx.executeWithoutResult(s -> produtos.findById(produtoId).orElseThrow().definirPoliticaDeLote(false, 30));
        assertThat(ids(produtoId)).containsExactly(longe);
        assertThat(lotes.findById(perto)).isPresent();
        // Margem exatamente igual aos dias que faltam: ainda é bloqueado? (hoje + 10 = validade => vendável)
        tx.executeWithoutResult(s -> produtos.findById(produtoId).orElseThrow().definirPoliticaDeLote(false, 10));
        assertThat(ids(produtoId)).containsExactly(perto, longe);
    }

    // ---------------------------------------------------------- constraints do banco

    @Test
    void bancoRecusaCodigoDuplicadoNoMesmoProdutoMesmoComCaixaDiferente() {
        long id = novoProduto(true);
        inserirLote(id, "Cx-1", 0);
        assertThatThrownBy(() -> inserirLote(id, "cx-1", 0)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void bancoRecusaSaldoNegativoStatusInvalidoCodigoVazioEProdutoInexistente() {
        long id = novoProduto(true);
        assertThatThrownBy(() -> inserirLote(id, codigo(), -1)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("""
                insert into lote_estoque (produto_id, codigo_lote, status, data_recebimento, usuario_id)
                values (?, ?, 'QUARENTENA', curdate(), ?)""", id, codigo(), adminId)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inserirLote(id, "   ", 0)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inserirLote(Long.MAX_VALUE, codigo(), 0)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void bancoRecusaMovimentoComLoteIncoerente() {
        long produtoId = novoProduto(true);
        long loteId = inserirLote(produtoId, codigo(), 5);
        // Lote sem os saldos do lote.
        assertThatThrownBy(() -> inserirMov(produtoId, "AJUSTE", 1, loteId, null, null)).isInstanceOf(DataAccessException.class);
        // Saldos do lote sem o lote.
        assertThatThrownBy(() -> inserirMov(produtoId, "AJUSTE", 1, null, 5, 6)).isInstanceOf(DataAccessException.class);
        // Conta que não fecha (I6): 5 + 1 != 9.
        assertThatThrownBy(() -> inserirMov(produtoId, "AJUSTE", 1, loteId, 5, 9)).isInstanceOf(DataAccessException.class);
        // Saldo do lote negativo.
        assertThatThrownBy(() -> inserirMov(produtoId, "AJUSTE", -2, loteId, 1, -1)).isInstanceOf(DataAccessException.class);
        // Lote inexistente.
        assertThatThrownBy(() -> inserirMov(produtoId, "AJUSTE", 1, Long.MAX_VALUE, 5, 6)).isInstanceOf(DataAccessException.class);
        // Tipo desconhecido.
        assertThatThrownBy(() -> inserirMov(produtoId, "ROUBO", 1, loteId, 5, 6)).isInstanceOf(DataAccessException.class);
        // Coerente passa, para cada tipo novo.
        inserirMov(produtoId, "PERDA", -1, loteId, 5, 4);
        inserirMov(produtoId, "VENCIMENTO", -1, loteId, 4, 3);
        inserirMov(produtoId, "REGULARIZACAO_LOTE", 2, loteId, 3, 5);
        // Movimento antigo, sem lote, continua válido (compatibilidade com a versão anterior).
        inserirMov(produtoId, "AJUSTE", 0 + 1, null, null, null);
    }

    @Test
    void tabelasDeAlocacaoRecusamQuantidadeInvalidaEDuplicidade() {
        long produtoId = novoProduto(true);
        long loteId = inserirLote(produtoId, codigo(), 5);
        long vendaId = inserirVendaComItem(produtoId);
        long itemVenda = jdbc.queryForObject("select id from item_venda where venda_id = ?", Long.class, vendaId);
        assertThatThrownBy(() -> jdbc.update("insert into item_venda_lote (item_venda_id, lote_id, quantidade) values (?, ?, 0)", itemVenda, loteId))
                .isInstanceOf(DataAccessException.class);
        jdbc.update("insert into item_venda_lote (item_venda_id, lote_id, quantidade) values (?, ?, 1)", itemVenda, loteId);
        assertThatThrownBy(() -> jdbc.update("insert into item_venda_lote (item_venda_id, lote_id, quantidade) values (?, ?, 1)", itemVenda, loteId))
                .isInstanceOf(DataAccessException.class);

        long itemCompra = inserirCompraComItem(produtoId);
        assertThatThrownBy(() -> jdbc.update("insert into item_compra_lote (item_compra_id, codigo_lote, quantidade) values (?, 'X', -1)", itemCompra))
                .isInstanceOf(DataAccessException.class);
        jdbc.update("insert into item_compra_lote (item_compra_id, codigo_lote, quantidade) values (?, 'X', 2)", itemCompra);
        assertThatThrownBy(() -> jdbc.update("insert into item_compra_lote (item_compra_id, codigo_lote, quantidade) values (?, 'x', 2)", itemCompra))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void produtoRecusaMargemNegativa() {
        long id = novoProduto(false);
        assertThatThrownBy(() -> jdbc.update("update produto set dias_bloqueio_validade = -1 where id = ?", id))
                .isInstanceOf(DataAccessException.class);
    }

    // ------------------------------------------------------------ índices (FEFO e FKs)

    @Test
    void indicesDoFefoExistemNaOrdemCerta() {
        assertThat(colunasDoIndice("lote_estoque", "idx_lote_fefo")).containsExactly("produto_id", "status", "validade", "id");
        assertThat(colunasDoIndice("lote_estoque", "uk_lote_produto_codigo")).containsExactly("produto_id", "codigo_lote");
        assertThat(colunasDoIndice("lote_estoque", "idx_lote_validade")).containsExactly("validade", "saldo");
        assertThat(colunasDoIndice("movimentacao_estoque", "idx_mov_estoque_lote_data")).containsExactly("lote_id", "created_at");
        assertThat(colunasDoIndice("movimentacao_estoque", "idx_mov_estoque_operacao")).containsExactly("operacao_id");
        assertThat(colunasDoIndice("item_venda_lote", "idx_item_venda_lote_lote")).containsExactly("lote_id");
    }

    @Test
    void consultaFefoUsaOIndiceDoFefo() {
        long produtoId = novoProduto(true);
        lote(produtoId, "IDX", LocalDate.now().plusDays(10), 1);
        var plano = jdbc.queryForList("explain select id from lote_estoque where produto_id = ? and status = 'DISPONIVEL' and saldo > 0 "
                + "order by validade, id", produtoId);
        assertThat(plano.toString()).contains("idx_lote_fefo");
    }

    // ------------------------------------------------------- rastreio (aceite da etapa)

    @Test
    void loteEhRastreavelAteSuasEntradasEFuturasSaidas() {
        long produtoId = novoProduto(true);
        long compraId = inserirCompraSimples();
        long vendaId = inserirVendaComItem(produtoId);
        long itemVenda = jdbc.queryForObject("select id from item_venda where venda_id = ?", Long.class, vendaId);
        long itemCompra = inserirItemCompra(compraId, produtoId);

        long loteId = tx.execute(s -> {
            Produto p = produtos.findById(produtoId).orElseThrow();
            Usuario u = usuarios.findById(adminId).orElseThrow();
            LoteEstoque lote = servico.obterOuCriar(p, codigo(), LocalDate.now().plusDays(80), new BigDecimal("7.00"), LocalDate.now(),
                    em.getReference(br.com.tecnodreamer.petconnect.compra.domain.Compra.class, compraId), null, u);
            ItemCompraLote recebido = new ItemCompraLote(em.getReference(ItemCompra.class, itemCompra), lote.getCodigoLote(), lote.getValidade(), 10);
            recebido.vincularLote(lote);
            itensCompraLote.save(recebido);
            int ant = p.movimentar(10);
            int antLote = lote.movimentar(10);
            movimentacoes.save(new MovimentacaoEstoque(p, TipoMovimentacao.ENTRADA_COMPRA, 10, ant, ant + 10,
                    em.getReference(br.com.tecnodreamer.petconnect.compra.domain.Compra.class, compraId), null, u, null, lote, antLote, null));
            // Venda: 3 unidades saem do lote e ficam alocadas ao item.
            int ant2 = p.movimentar(-3);
            int antLote2 = lote.movimentar(-3);
            movimentacoes.save(new MovimentacaoEstoque(p, TipoMovimentacao.SAIDA_VENDA, -3, ant2, ant2 - 3, null, vendaId, u, null, lote, antLote2, null));
            itensVendaLote.save(new ItemVendaLote(em.getReference(ItemVenda.class, itemVenda), lote, 3));
            return lote.getId();
        });

        assertThat(itensCompraLote.findByLoteIdOrderByIdAsc(loteId)).singleElement().satisfies(i -> {
            assertThat(i.getQuantidade()).isEqualTo(10);
            assertThat(i.getItemCompra().getId()).isEqualTo(itemCompra);
        });
        assertThat(itensVendaLote.findByLoteIdOrderByIdAsc(loteId)).singleElement().satisfies(i -> assertThat(i.getQuantidade()).isEqualTo(3));
        assertThat(itensVendaLote.findByItemVendaIdOrderByIdAsc(itemVenda)).hasSize(1);
        assertThat(jdbc.queryForList("select tipo, quantidade, compra_id, venda_id, lote_saldo_anterior, lote_saldo_resultante "
                + "from movimentacao_estoque where lote_id = ? order by id", loteId))
                .hasSize(2)
                .satisfies(l -> {
                    assertThat(l.get(0).get("tipo")).isEqualTo("ENTRADA_COMPRA");
                    assertThat(((Number) l.get(0).get("compra_id")).longValue()).isEqualTo(compraId);
                    assertThat(l.get(1).get("tipo")).isEqualTo("SAIDA_VENDA");
                    assertThat(((Number) l.get(1).get("venda_id")).longValue()).isEqualTo(vendaId);
                });
        assertThat(lotes.findById(loteId).orElseThrow().getSaldo()).isEqualTo(7);
        assertThat(lotes.divergenciasDeSaldoDoLote()).doesNotContain(loteId);
        assertThat(lotes.divergenciasDoProdutoControlado()).doesNotContain(produtoId);
    }

    @Test
    void regularizacaoMoveSaldoParaOLoteSemAlterarOTotalDoProduto() {
        // Produto legado: 8 unidades sem lote (movimento AJUSTE antigo), controle ainda desligado.
        long produtoId = novoProduto(false);
        tx.executeWithoutResult(s -> {
            Produto p = produtos.findById(produtoId).orElseThrow();
            int ant = p.movimentar(8);
            movimentacoes.save(new MovimentacaoEstoque(p, TipoMovimentacao.AJUSTE, 8, ant, ant + 8, null,
                    usuarios.findById(adminId).orElseThrow(), "saldo legado"));
        });
        // Regularização: 8 unidades viram o lote L, o total continua 8 e o controle liga.
        long loteId = tx.execute(s -> {
            Produto p = produtos.findById(produtoId).orElseThrow();
            Usuario u = usuarios.findById(adminId).orElseThrow();
            LoteEstoque lote = servico.obterOuCriar(p, codigo(), LocalDate.now().plusDays(45), null, LocalDate.now(), null, null, u);
            int antLote = lote.movimentar(8);
            movimentacoes.save(new MovimentacaoEstoque(p, TipoMovimentacao.REGULARIZACAO_LOTE, 8, 8, 8, null, null, u,
                    "regularização", lote, antLote, UUID.randomUUID().toString()));
            p.ativarControleLote();
            return lote.getId();
        });
        assertThat(saldoProduto(produtoId)).isEqualTo(8);
        assertThat(lotes.findById(loteId).orElseThrow().getSaldo()).isEqualTo(8);
        assertThat(lotes.divergenciasDoProdutoControlado()).doesNotContain(produtoId);
        assertThat(lotes.divergenciasDeSaldoDoLote()).doesNotContain(loteId);
        // I1 exclui a regularização: 8 (ajuste) == total.
        assertThat(jdbc.queryForObject("select sum(quantidade) from movimentacao_estoque where produto_id = ? and tipo <> 'REGULARIZACAO_LOTE'",
                Integer.class, produtoId)).isEqualTo(8);
    }

    // ------------------------------------------------------------------ apoio

    private long novoProduto(boolean controlaLote) {
        return tx.execute(s -> {
            var categoria = categorias.findAll().get(0);
            Produto p = produtos.save(new Produto("Lote IT " + UUID.randomUUID(), null, categoria, null, null,
                    new BigDecimal("5.00"), new BigDecimal("9.90"), 1));
            if (controlaLote) {
                p.ativarControleLote();
            }
            return p.getId();
        });
    }

    private static String codigo() {
        return "L" + ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L);
    }

    private long lote(long produtoId, String prefixo, LocalDate validade, int saldo) {
        return tx.execute(s -> {
            Produto p = produtos.findById(produtoId).orElseThrow();
            Usuario u = usuarios.findById(adminId).orElseThrow();
            // A política do produto pode permitir lote sem validade; para o teste de validade nula ela é ligada antes.
            LoteEstoque l = lotes.save(new LoteEstoque(p, prefixo + "-" + codigo(), validade, null, LocalDate.now(), null, null, u));
            if (saldo > 0) {
                int antLote = l.movimentar(saldo);
                int ant = p.movimentar(saldo);
                movimentacoes.save(new MovimentacaoEstoque(p, TipoMovimentacao.AJUSTE, saldo, ant, ant + saldo, null, null, u, "teste", l, antLote, null));
            }
            return l.getId();
        });
    }

    private List<Long> ids(long produtoId) {
        return tx.execute(s -> servico.vendaveis(produtos.findById(produtoId).orElseThrow()).stream().map(LoteEstoque::getId).toList());
    }

    private void movimentarEntrada(Produto p, LoteEstoque lote, int quantidade, Usuario u) {
        int ant = p.movimentar(quantidade);
        int antLote = lote.movimentar(quantidade);
        movimentacoes.save(new MovimentacaoEstoque(p, TipoMovimentacao.AJUSTE, quantidade, ant, ant + quantidade, null, null, u, "entrada de teste", lote, antLote, null));
    }

    private int saldoProduto(long produtoId) {
        return jdbc.queryForObject("select quantidade_estoque from produto where id = ?", Integer.class, produtoId);
    }

    private long inserirLote(long produtoId, String codigo, int saldo) {
        jdbc.update("""
                insert into lote_estoque (produto_id, codigo_lote, validade, saldo, data_recebimento, usuario_id)
                values (?, ?, date_add(curdate(), interval 30 day), ?, curdate(), ?)""", produtoId, codigo, saldo, adminId);
        return jdbc.queryForObject("select id from lote_estoque where produto_id = ? and codigo_lote = ?", Long.class, produtoId, codigo);
    }

    private void inserirMov(long produtoId, String tipo, int quantidade, Long loteId, Integer loteAnt, Integer loteRes) {
        jdbc.update("""
                insert into movimentacao_estoque (produto_id, tipo, quantidade, saldo_anterior, saldo_resultante, usuario_id,
                                                  lote_id, lote_saldo_anterior, lote_saldo_resultante)
                values (?, ?, ?, 0, 0, ?, ?, ?, ?)""", produtoId, tipo, quantidade, adminId, loteId, loteAnt, loteRes);
    }

    private long inserirCompraSimples() {
        long fornecedor = fornecedor();
        jdbc.update("insert into compra (fornecedor_id, data_compra, valor_total) values (?, curdate(), 70.00)", fornecedor);
        return jdbc.queryForObject("select max(id) from compra where fornecedor_id = ?", Long.class, fornecedor);
    }

    private long inserirItemCompra(long compraId, long produtoId) {
        jdbc.update("insert into item_compra (compra_id, produto_id, quantidade, preco_custo_unitario) values (?, ?, 10, 7.00)", compraId, produtoId);
        return jdbc.queryForObject("select max(id) from item_compra where compra_id = ?", Long.class, compraId);
    }

    private long inserirCompraComItem(long produtoId) {
        return inserirItemCompra(inserirCompraSimples(), produtoId);
    }

    private long inserirVendaComItem(long produtoId) {
        jdbc.update("insert into venda (valor_total, forma_pagamento, usuario_id) values (9.90, 'PIX', ?)", adminId);
        long vendaId = jdbc.queryForObject("select max(id) from venda where usuario_id = ?", Long.class, adminId);
        jdbc.update("insert into item_venda (venda_id, produto_id, quantidade, preco_unitario) values (?, ?, 3, 9.90)", vendaId, produtoId);
        return vendaId;
    }

    private long fornecedor() {
        String cnpj = String.format("%014d", ThreadLocalRandom.current().nextLong(10_000_000_000_000L, 99_999_999_999_999L));
        jdbc.update("insert into fornecedor (nome_fantasia, cnpj) values ('Lote IT', ?)", cnpj);
        return jdbc.queryForObject("select id from fornecedor where cnpj = ?", Long.class, cnpj);
    }

    private List<String> colunasDoIndice(String tabela, String indice) {
        return jdbc.queryForList("""
                select column_name from information_schema.statistics
                where table_schema = database() and table_name = ? and index_name = ?
                order by seq_in_index""", String.class, tabela, indice);
    }
}
