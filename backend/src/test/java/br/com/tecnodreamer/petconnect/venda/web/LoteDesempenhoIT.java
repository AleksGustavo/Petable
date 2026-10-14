package br.com.tecnodreamer.petconnect.venda.web;

import br.com.tecnodreamer.petconnect.produto.application.ReconciliacaoEstoqueService;
import br.com.tecnodreamer.petconnect.produto.domain.LoteEstoque;
import br.com.tecnodreamer.petconnect.produto.infrastructure.LoteEstoqueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Não funcional (etapa 05): a consulta FEFO, a venda, a consulta de lotes, os alertas e a reconciliação com volume representativo
 * (300 produtos × 100 lotes = 30.000 lotes). Os dados são inseridos já consistentes (lote + movimento) e removidos no fim, para
 * não afetar os outros testes do banco compartilhado. Os limites são folgados (CI é mais lento que a máquina do dono); os tempos
 * medidos vão para a saída ({@code [DESEMPENHO]}) e para o relatório.
 */
class LoteDesempenhoIT extends LoteIntegrationTest {

    private static final int PRODUTOS = 300;
    private static final int LOTES_POR_PRODUTO = 100;
    private static final String PREFIXO = "Desempenho Lote ";

    @Autowired
    LoteEstoqueRepository lotesRepo2;
    @Autowired
    ReconciliacaoEstoqueService reconciliacao;
    @Autowired
    br.com.tecnodreamer.petconnect.produto.infrastructure.ProdutoRepository produtosRepo;
    @Autowired
    org.springframework.transaction.PlatformTransactionManager tm;

    private long ms(Runnable r) {
        long t = System.nanoTime();
        r.run();
        return (System.nanoTime() - t) / 1_000_000;
    }

    private void semear(long categoria, long usuario) {
        LocalDate hoje = LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"));
        Random sorteio = new Random(7);
        for (int p = 0; p < PRODUTOS; p++) {
            jdbc.update("insert into produto (nome, categoria_id, preco_custo, preco_venda, quantidade_estoque, estoque_minimo, controla_lote) values (?, ?, 5.00, 10.00, 0, 1, true)",
                    PREFIXO + p, categoria);
        }
        List<Long> produtos = jdbc.queryForList("select id from produto where nome like ? order by id", Long.class, PREFIXO + "%");
        List<Object[]> lotes = new ArrayList<>();
        for (long produto : produtos) {
            for (int l = 0; l < LOTES_POR_PRODUTO; l++) {
                int saldo = sorteio.nextInt(10) < 2 ? 0 : 1 + sorteio.nextInt(20);              // 20% zerados
                boolean bloqueado = sorteio.nextInt(20) == 0;                                      // 5% em quarentena
                LocalDate validade = hoje.plusDays(sorteio.nextInt(400) - 60);                   // de 60 dias vencido a ~13 meses
                lotes.add(new Object[] {produto, "D" + l, Date.valueOf(validade), saldo, bloqueado ? "BLOQUEADO" : "DISPONIVEL", Date.valueOf(hoje), usuario});
            }
        }
        jdbc.batchUpdate("insert into lote_estoque (produto_id, codigo_lote, validade, saldo, status, data_recebimento, usuario_id) values (?, ?, ?, ?, ?, ?, ?)", lotes);
        // Um movimento de entrada por lote e o total do produto, para o conjunto nascer consistente (I1, I2, I3).
        jdbc.update("""
                insert into movimentacao_estoque (produto_id, tipo, quantidade, saldo_anterior, saldo_resultante, usuario_id, lote_id, lote_saldo_anterior, lote_saldo_resultante, motivo)
                select l.produto_id, 'AJUSTE', l.saldo, 0, 0, ?, l.id, 0, l.saldo, 'carga de desempenho'
                from lote_estoque l join produto p on p.id = l.produto_id where p.nome like ? and l.saldo > 0""", usuario, PREFIXO + "%");
        jdbc.update("update produto p set p.quantidade_estoque = (select coalesce(sum(l.saldo), 0) from lote_estoque l where l.produto_id = p.id) where p.nome like ?", PREFIXO + "%");
        // saldo_anterior/resultante do total do produto só precisam respeitar o CHECK (>= 0); a soma por produto é o que a I1 confere.
    }

    private void limpar() {
        jdbc.update("delete m from movimentacao_estoque m join produto p on p.id = m.produto_id where p.nome like ?", PREFIXO + "%");
        jdbc.update("delete il from item_venda_lote il join lote_estoque l on l.id = il.lote_id join produto p on p.id = l.produto_id where p.nome like ?", PREFIXO + "%");
        jdbc.update("delete l from lote_estoque l join produto p on p.id = l.produto_id where p.nome like ?", PREFIXO + "%");
        jdbc.update("delete iv from item_venda iv join produto p on p.id = iv.produto_id where p.nome like ?", PREFIXO + "%");
        jdbc.update("delete from produto where nome like ?", PREFIXO + "%");
    }

    @Test
    void consultaFefoVendaListagemAlertasEReconciliacaoComTrintaMilLotes() throws Exception {
        long categoria = jdbc.queryForObject("select id from categoria_produto limit 1", Long.class);
        long adminId = adminId();
        admin();
        limpar();
        try {
            long carga = ms(() -> semear(categoria, adminId));
            assertThat(jdbc.queryForObject("select count(*) from lote_estoque l join produto p on p.id = l.produto_id where p.nome like ?", Long.class, PREFIXO + "%"))
                    .isEqualTo((long) PRODUTOS * LOTES_POR_PRODUTO);
            List<Long> produtos = jdbc.queryForList("select id from produto where nome like ? order by id", Long.class, PREFIXO + "%");
            LocalDate hoje = LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"));

            // 1) Consulta FEFO: 100 produtos sorteados, cada um 3 vezes; p95 e plano de execução.
            Random sorteio = new Random(11);
            List<Long> tempos = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                long produto = produtos.get(sorteio.nextInt(produtos.size()));
                for (int k = 0; k < 3; k++) {
                    long t = System.nanoTime();
                    List<LoteEstoque> vendaveis = lotesRepo2.vendaveisEmOrdemFefo(produto, hoje);
                    tempos.add((System.nanoTime() - t) / 1_000_000);
                    assertThat(vendaveis).isNotEmpty();
                    // Ordem FEFO: validade crescente, sem validade por último, sem vencido e sem bloqueado.
                    LocalDate anterior = LocalDate.MIN;
                    for (LoteEstoque l : vendaveis) {
                        assertThat(l.getValidade()).isNotNull();
                        assertThat(l.getValidade()).isAfterOrEqualTo(hoje);
                        assertThat(l.getValidade()).isAfterOrEqualTo(anterior);
                        anterior = l.getValidade();
                    }
                }
            }
            Collections.sort(tempos);
            long p95 = tempos.get((int) (tempos.size() * 0.95) - 1);
            long mediana = tempos.get(tempos.size() / 2);
            var plano = jdbc.queryForList("explain select id from lote_estoque where produto_id = ? and status = 'DISPONIVEL' and saldo > 0 order by validade, id", produtos.get(0));
            System.out.printf("[DESEMPENHO] carga de %d lotes: %d ms%n", PRODUTOS * LOTES_POR_PRODUTO, carga);
            System.out.printf("[DESEMPENHO] consulta FEFO (100 lotes por produto): mediana %d ms, p95 %d ms, plano %s%n", mediana, p95, plano);
            assertThat(plano.toString()).contains("idx_lote_fefo");
            assertThat(p95).as("p95 da consulta FEFO").isLessThan(150);

            // 2) O núcleo da venda sobre 100 lotes: trava do produto, leitura travada dos lotes e alocação FEFO. Reverte no fim
            //    (nenhuma venda de teste fica gravada no banco compartilhado).
            long produtoVenda = produtos.get(5);
            int[] alocado = new int[1];
            long venda = ms(() -> new org.springframework.transaction.support.TransactionTemplate(tm).executeWithoutResult(s -> {
                var p = produtosRepo.travar(produtoVenda).orElseThrow();
                var vendaveis = lotesRepo2.travarVendaveisEmOrdemFefo(p.getId(), hoje);
                int total = vendaveis.stream().mapToInt(LoteEstoque::getSaldo).sum();
                var alocacoes = br.com.tecnodreamer.petconnect.produto.application.AlocadorFefo.alocar(vendaveis, Math.min(40, total)).orElseThrow();
                alocado[0] = alocacoes.size();
                s.setRollbackOnly();
            }));
            System.out.printf("[DESEMPENHO] trava + leitura travada + alocação FEFO de 40 unidades (%d lotes usados): %d ms%n", alocado[0], venda);
            assertThat(venda).as("núcleo da venda com FEFO").isLessThan(1500);

            // 3) Listagem paginada, alertas e rastreabilidade sobre 30 mil lotes.
            long listagem = ms(() -> {
                try {
                    mvc.perform(get("/api/v1/lotes").param("busca", PREFIXO + "7").param("size", "20").with(bearer(admin()))).andExpect(status().isOk());
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            long janela = ms(() -> {
                try {
                    mvc.perform(get("/api/v1/lotes").param("venceEmDias", "90").param("size", "50").with(bearer(admin()))).andExpect(status().isOk());
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            long alertas = ms(() -> {
                try {
                    mvc.perform(get("/api/v1/estoque/alertas").with(bearer(admin()))).andExpect(status().isOk());
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            System.out.printf("[DESEMPENHO] GET /lotes busca por produto: %d ms; GET /lotes vencendo em 90 dias (30 mil lotes): %d ms; GET /estoque/alertas: %d ms%n",
                    listagem, janela, alertas);
            assertThat(listagem).isLessThan(1500);
            assertThat(janela).isLessThan(2500);
            assertThat(alertas).isLessThan(2500);

            // 4) Reconciliação em lote sobre todo o banco: consistente para a carga e rápida.
            var reconc = new Object() {
                br.com.tecnodreamer.petconnect.produto.web.ReconciliacaoEstoqueResponse r;
            };
            long reconciliacaoMs = ms(() -> reconc.r = reconciliacao.reconciliar());
            System.out.printf("[DESEMPENHO] reconciliação I1/I2/I3/I5/I7 sobre %d lotes: %d ms%n", reconc.r.lotes(), reconciliacaoMs);
            assertThat(reconc.r.divergencias()).noneMatch(d -> d.produtoId() != null && produtos.contains(d.produtoId()));
            assertThat(reconciliacaoMs).isLessThan(10_000);
        } finally {
            limpar();
        }
    }
}
