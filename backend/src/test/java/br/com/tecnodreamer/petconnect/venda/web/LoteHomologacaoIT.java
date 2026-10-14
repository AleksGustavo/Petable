package br.com.tecnodreamer.petconnect.venda.web;

import br.com.tecnodreamer.petconnect.compra.application.CompraService;
import br.com.tecnodreamer.petconnect.produto.application.ReconciliacaoEstoqueService;
import br.com.tecnodreamer.petconnect.produto.domain.EstoqueInsuficienteException;
import br.com.tecnodreamer.petconnect.venda.application.RegistroVendaIdempotente;
import br.com.tecnodreamer.petconnect.venda.application.ResultadoVenda;
import br.com.tecnodreamer.petconnect.venda.domain.FormaPagamento;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Homologação do controle de lote (etapa 05): os cenários de bloqueio de liberação que ainda não tinham teste próprio.
 * Cada método leva o código H-xx citado em {@code 05_RELATORIO_HOMOLOGACAO.md}.
 */
class LoteHomologacaoIT extends LoteIntegrationTest {

    @Autowired
    RegistroVendaIdempotente registro;
    @Autowired
    CompraService compras;
    @Autowired
    ReconciliacaoEstoqueService reconciliacao;

    private String pix(long... itens) {
        return vendaJson("PIX", "0.00", "null", itens);
    }

    private static VendaRequest pedido(long... produtoEQuantidade) {
        List<VendaRequest.Item> itens = new ArrayList<>();
        for (int i = 0; i < produtoEQuantidade.length; i += 2) {
            itens.add(new VendaRequest.Item(produtoEQuantidade[i], (int) produtoEQuantidade[i + 1]));
        }
        return new VendaRequest(null, itens, null, FormaPagamento.PIX, null, null);
    }

    private static List<Object> emParalelo(List<Callable<Object>> tarefas) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tarefas.size());
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<Object>> futuros = new ArrayList<>();
            for (Callable<Object> t : tarefas) {
                futuros.add(pool.submit(() -> {
                    largada.await();
                    return t.call();
                }));
            }
            largada.countDown();
            List<Object> resultados = new ArrayList<>();
            for (Future<Object> f : futuros) {
                try {
                    resultados.add(f.get(120, TimeUnit.SECONDS));
                } catch (ExecutionException e) {
                    resultados.add(e.getCause());
                }
            }
            return resultados;
        } finally {
            pool.shutdownNow();
        }
    }

    /** A reconciliação global não pode apontar nada dos produtos que este teste criou. */
    private void exigirConsistente(long... produtos) {
        var r = reconciliacao.reconciliar();
        for (long p : produtos) {
            assertThat(r.divergencias()).as("divergências do produto " + p).noneMatch(d -> d.produtoId() != null && d.produtoId() == p);
        }
    }

    // H-01 ------------------------------------------------------------------------------------------------

    @Test
    void h01_estornoRestauraOLoteOriginalMasLoteEmQuarentenaNaoVoltaAVenda() throws Exception {
        long p = produtoControlado("10.00", "A:60:5");
        long venda = venderOk(admin(), pix(p, 3));
        // Depois da venda o fabricante recolhe o lote: quarentena.
        postLote("/api/v1/produtos/" + p + "/lotes/" + loteId(p, "A") + "/bloquear", "{\"motivo\": \"Recolhimento do fabricante\"}").andExpect(status().isOk());

        cancelarVenda(admin(), venda, "RETORNAR").andExpect(status().isOk());

        // O saldo volta ao lote correto (rastro e contagem física), mas o lote continua indisponível para venda.
        assertThat(saldoLote(p, "A")).isEqualTo(5);
        vender(admin(), chave(), pix(p, 1)).andExpect(status().isConflict()).andExpect(jsonPath("$.disponivel").value(0));
        mvc.perform(get("/api/v1/produtos/busca-rapida").param("q", jdbc.queryForObject("select nome from produto where id = ?", String.class, p)).with(bearer(admin())))
                .andExpect(jsonPath("$[0].quantidadeVendavel").value(0)).andExpect(jsonPath("$[0].disponivel").value(false));
        postLote("/api/v1/produtos/" + p + "/lotes/" + loteId(p, "A") + "/desbloquear", "{\"motivo\": \"Liberado pelo fabricante\"}").andExpect(status().isOk());
        venderOk(admin(), pix(p, 1));
        exigirConsistente(p);
    }

    // H-02 ------------------------------------------------------------------------------------------------

    @Test
    void h02_compraComVariosLotesMantemValorTotalContaAPagarECustoMedio() throws Exception {
        long p = produtoControlado("20.00");
        long fornecedor = criarFornecedor(unico("Fornecedor Homologação"));
        long compra = criarCompra(fornecedor, String.valueOf(p), "10:7.00");

        receberComLotes(compra, p, "L1:60:2", "L2:90:3", "L3:120:5").andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select valor_total from compra where id = ?", String.class, compra)).isEqualTo("70.00");
        assertThat(jdbc.queryForObject("select valor_original from conta_pagar where compra_id = ?", String.class, compra)).isEqualTo("70.00");
        assertThat(custo(p)).isEqualTo("7.00");
        assertThat(totalLotes(p)).isEqualTo(10);
        assertThat(saldo(p)).isEqualTo(10);
        assertThat(jdbc.queryForObject("select count(*) from lote_estoque where produto_id = ? and compra_id = ?", Integer.class, p, compra)).isEqualTo(3);
        // Custo de entrada preservado por lote e custo médio ponderado na segunda compra (10 a 7,00 + 10 a 9,00 = 8,00).
        long segunda = criarCompra(fornecedor, String.valueOf(p), "10:9.00");
        receberComLotes(segunda, p, "L4:150:10").andExpect(status().isOk());
        assertThat(custo(p)).isEqualTo("8.00");
        assertThat(jdbc.queryForObject("select custo_unitario from lote_estoque where produto_id = ? and codigo_lote = 'L4'", String.class, p)).isEqualTo("9.00");
        assertThat(jdbc.queryForObject("select custo_unitario from lote_estoque where produto_id = ? and codigo_lote = 'L1'", String.class, p)).isEqualTo("7.00");
        exigirConsistente(p);
    }

    // H-04 ------------------------------------------------------------------------------------------------

    @Test
    void h04_nenhumaRotaDeSaidaOuEntradaIgnoraOLoteDeProdutoControlado() throws Exception {
        long p = produtoControlado("10.00", "A:60:5");
        String ajustes = "/api/v1/produtos/" + p + "/ajustes-estoque";
        long lote = loteId(p, "A");

        // Ajuste sem lote, saída identificada só pelo código, perda de lote inexistente: nada passa e nada muda.
        postLote(ajustes, "{\"quantidade\": -1, \"motivo\": \"Sem informar lote\"}").andExpect(status().isBadRequest());
        postLote(ajustes, "{\"quantidade\": 1, \"motivo\": \"Sem informar lote\"}").andExpect(status().isBadRequest());
        postLote(ajustes, "{\"quantidade\": -1, \"motivo\": \"Só o código\", \"codigoLote\": \"A\"}").andExpect(status().isBadRequest());
        postLote(ajustes, "{\"quantidade\": -9, \"motivo\": \"Passa do lote\", \"loteId\": " + lote + "}").andExpect(status().isConflict());
        postLote("/api/v1/produtos/" + p + "/lotes/999999999/perdas", "{\"quantidade\": 1, \"motivo\": \"Lote inexistente\"}").andExpect(status().isNotFound());
        cancelarVenda(admin(), venderOk(admin(), pix(p, 1)), null).andExpect(status().isBadRequest());
        assertThat(saldoLote(p, "A")).isEqualTo(4);

        // Toda movimentação do produto, sem exceção, carrega lote e os dois saldos do lote.
        assertThat(jdbc.queryForObject("select count(*) from movimentacao_estoque where produto_id = ? and tipo <> 'AJUSTE' and lote_id is null", Integer.class, p)).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from movimentacao_estoque where produto_id = ? and lote_id is not null
                  and (lote_saldo_anterior is null or lote_saldo_resultante is null)""", Integer.class, p)).isZero();
        assertThat(reconciliacao.reconciliar().divergencias()).noneMatch(d -> "I7".equals(d.regra()) && d.produtoId() != null && d.produtoId() == p);
        exigirConsistente(p);
    }

    // H-05 ------------------------------------------------------------------------------------------------

    @Test
    void h05_vinteCaixasSimultaneosSobreDozeUnidadesConfirmamExatamenteDoze() throws Exception {
        long p = produtoControlado("10.00", "A:20:4", "B:40:5", "C:60:3");
        long adminId = adminId();
        admin();

        List<Callable<Object>> tarefas = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tarefas.add(() -> registro.registrar(pedido(p, 1), UUID.randomUUID().toString(), adminId));
        }
        List<Object> r = emParalelo(tarefas);

        assertThat(r).as(r.toString()).filteredOn(ResultadoVenda.class::isInstance).hasSize(12);
        assertThat(r).filteredOn(EstoqueInsuficienteException.class::isInstance).hasSize(8);
        assertThat(saldoLote(p, "A") + saldoLote(p, "B") + saldoLote(p, "C")).isZero();
        assertThat(movimentacoes(p, "SAIDA_VENDA")).isEqualTo(12);
        exigirConsistente(p);
    }

    // H-06 ------------------------------------------------------------------------------------------------

    @Test
    void h06_vendasComOsMesmosProdutosEmOrdensOpostasNaoDaoDeadlock() throws Exception {
        long a = produtoControlado("10.00", "A1:30:40");
        long b = produtoControlado("10.00", "B1:30:40");
        long adminId = adminId();
        admin();

        List<Callable<Object>> tarefas = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tarefas.add(() -> registro.registrar(pedido(a, 1, b, 1), UUID.randomUUID().toString(), adminId));
            tarefas.add(() -> registro.registrar(pedido(b, 1, a, 1), UUID.randomUUID().toString(), adminId));
        }
        List<Object> r = emParalelo(tarefas);

        assertThat(r).as(r.toString()).allMatch(ResultadoVenda.class::isInstance);
        assertThat(saldoLote(a, "A1")).isEqualTo(20);
        assertThat(saldoLote(b, "B1")).isEqualTo(20);
        exigirConsistente(a, b);
    }

    // H-07 ------------------------------------------------------------------------------------------------

    @Test
    void h07_errosDasRotasDeLoteNaoVazamDetalheInternoNemSql() throws Exception {
        long p = produtoControlado("10.00", "A:60:5");
        String[] corposRuins = {
                "{}", "{\"quantidade\": \"abc\", \"motivo\": \"x\"}", "{\"quantidade\": 99999999999, \"motivo\": \"Quantidade enorme\"}",
                "{\"quantidade\": 1, \"motivo\": \"" + "x".repeat(600) + "\"}", "não é json", "[]",
                "{\"quantidade\": 1, \"motivo\": \"Código com SQL\", \"codigoLote\": \"'; DROP TABLE lote_estoque; --\", \"validade\": \"2030-01-01\"}"};
        for (String corpo : corposRuins) {
            String resposta = mvc.perform(post("/api/v1/produtos/" + p + "/ajustes-estoque").with(bearer(admin())).contentType(MediaType.APPLICATION_JSON).content(corpo))
                    .andReturn().getResponse().getContentAsString();
            semDetalheInterno(resposta);
        }
        String[] urls = {"/api/v1/lotes?size=5000", "/api/v1/lotes?status=INEXISTENTE", "/api/v1/lotes?venceEmDias=abc", "/api/v1/lotes?produtoId=x",
                "/api/v1/lotes?busca=%27%3B%20DROP%20TABLE%20lote_estoque%3B--", "/api/v1/lotes/abc/rastreio", "/api/v1/lotes/0/rastreio"};
        for (String url : urls) {
            var resultado = mvc.perform(get(url).with(bearer(admin()))).andReturn().getResponse();
            assertThat(resultado.getStatus()).as(url).isBetween(200, 499);
            semDetalheInterno(resultado.getContentAsString());
        }
        // O comando malicioso virou apenas texto: foi gravado como o código de um lote novo (a requisição era válida), a tabela
        // segue existindo e o lote original está intacto.
        assertThat(jdbc.queryForObject("select count(*) from lote_estoque where produto_id = ?", Integer.class, p)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from lote_estoque where produto_id = ? and codigo_lote = ?", Integer.class, p, "'; DROP TABLE lote_estoque; --")).isEqualTo(1);
        assertThat(saldoLote(p, "A")).isEqualTo(5);
    }

    private static void semDetalheInterno(String corpo) {
        assertThat(corpo).doesNotContain("Exception", "org.springframework", "java.lang", "at br.com", "SELECT ", "select ", "jdbc", "Hibernate", "stackTrace", "SQLSyntax");
    }

    // H-08 ------------------------------------------------------------------------------------------------

    @Test
    void h08_sequenciaAleatoriaDeOperacoesMantemAsInvariantesAposCadaPasso() throws Exception {
        long seed = 20261014L;
        Random sorteio = new Random(seed);
        long p = produtoControlado("10.00", "A:20:30", "B:40:30", "C:60:30");
        List<Long> vendasAbertas = new ArrayList<>();
        String[] lotes = {"A", "B", "C"};
        int aceitas = 0;

        for (int passo = 1; passo <= 60; passo++) {
            int op = sorteio.nextInt(7);
            String lote = lotes[sorteio.nextInt(lotes.length)];
            String base = "/api/v1/produtos/" + p + "/lotes/" + loteId(p, lote);
            switch (op) {
                case 0, 1, 2 -> {
                    var r = vender(admin(), chave(), pix(p, 1 + sorteio.nextInt(4))).andReturn().getResponse();
                    assertThat(r.getStatus()).as("passo " + passo + " (seed " + seed + ")").isIn(201, 409);
                    if (r.getStatus() == 201) {
                        aceitas++;
                        vendasAbertas.add(((Number) com.jayway.jsonpath.JsonPath.read(r.getContentAsString(), "$.id")).longValue());
                    }
                }
                case 3 -> {
                    if (!vendasAbertas.isEmpty()) {
                        long venda = vendasAbertas.remove(sorteio.nextInt(vendasAbertas.size()));
                        cancelarVenda(admin(), venda, sorteio.nextBoolean() ? "RETORNAR" : "DESCARTAR").andExpect(status().isOk());
                    }
                }
                case 4 -> postLote(base + "/perdas", "{\"quantidade\": 1, \"motivo\": \"Perda do passo " + passo + "\"}");
                case 5 -> postLote("/api/v1/produtos/" + p + "/ajustes-estoque",
                        "{\"quantidade\": " + (sorteio.nextBoolean() ? 2 : -1) + ", \"motivo\": \"Inventário do passo " + passo + "\", \"loteId\": " + loteId(p, lote) + "}");
                default -> postLote(base + (sorteio.nextBoolean() ? "/bloquear" : "/desbloquear"), "{\"motivo\": \"Quarentena do passo " + passo + "\"}");
            }
            // Invariantes depois de CADA passo (aceito ou recusado): total = soma dos lotes = soma dos movimentos.
            assertThat(saldo(p)).as("I3 no passo " + passo + " (seed " + seed + ")").isEqualTo(totalLotes(p));
            assertThat(jdbc.queryForObject("select coalesce(sum(quantidade), 0) from movimentacao_estoque where produto_id = ? and tipo <> 'REGULARIZACAO_LOTE'", Integer.class, p))
                    .as("I1 no passo " + passo).isEqualTo(saldo(p));
            for (String l : lotes) {
                assertThat(jdbc.queryForObject("select coalesce(sum(quantidade), 0) from movimentacao_estoque where lote_id = ?", Integer.class, loteId(p, l)))
                        .as("I2 no passo " + passo + " lote " + l).isEqualTo(saldoLote(p, l));
                assertThat(saldoLote(p, l)).isGreaterThanOrEqualTo(0);
            }
        }
        assertThat(aceitas).as("o sorteio precisa ter exercitado vendas").isGreaterThan(5);
        exigirConsistente(p);
    }

    // H-09 ------------------------------------------------------------------------------------------------

    @Test
    void h09_reconciliacaoEmLoteDetectaDivergenciasInjetadasECorrigeComAjusteExplicito() throws Exception {
        long p = produtoControlado("10.00", "A:60:5", "B:90:4");
        exigirConsistente(p);

        // Adultera por fora do livro-razão: saldo do lote e total do produto.
        jdbc.update("update lote_estoque set saldo = 9 where id = ?", loteId(p, "A"));
        jdbc.update("update produto set quantidade_estoque = quantidade_estoque + 3 where id = ?", p);

        var r = reconciliacao.reconciliar();
        assertThat(r.consistente()).isFalse();
        assertThat(r.divergencias()).filteredOn(d -> d.produtoId() != null && d.produtoId() == p).extracting(d -> d.regra())
                .contains("I1", "I2", "I3");
        var i2 = r.divergencias().stream().filter(d -> "I2".equals(d.regra()) && d.loteId() != null && d.loteId() == loteId(p, "A")).findFirst().orElseThrow();
        assertThat(i2.esperado()).isEqualTo(5);
        assertThat(i2.encontrado()).isEqualTo(9);

        // Endpoint: só ADMIN, devolve as mesmas divergências.
        mvc.perform(get("/api/v1/estoque/reconciliacao").with(comPapel("VENDAS"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/estoque/reconciliacao").with(bearer(admin()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.consistente").value(false))
                .andExpect(jsonPath("$.divergencias[?(@.produtoId==" + p + ")]").isNotEmpty());

        // Desfaz a adulteração (a correção real seria um ajuste auditado, nunca editar o histórico).
        jdbc.update("update lote_estoque set saldo = 5 where id = ?", loteId(p, "A"));
        jdbc.update("update produto set quantidade_estoque = quantidade_estoque - 3 where id = ?", p);
        exigirConsistente(p);
    }

    @Test
    void h09b_movimentoSemLoteDepoisQueOProdutoTemLoteEViolacaoI7() throws Exception {
        long p = produtoControlado("10.00", "A:60:5");
        // Simula um caminho que ignorou o lote: movimento sem lote, depois de o produto já ter lote.
        jdbc.update("""
                insert into movimentacao_estoque (produto_id, tipo, quantidade, saldo_anterior, saldo_resultante, usuario_id, motivo)
                values (?, 'AJUSTE', 1, 5, 6, ?, 'caminho que ignorou o lote')""", p, adminId());
        jdbc.update("update produto set quantidade_estoque = 6 where id = ?", p);

        var r = reconciliacao.reconciliar();
        assertThat(r.divergencias()).anyMatch(d -> "I7".equals(d.regra()) && d.produtoId() != null && d.produtoId() == p);
        assertThat(r.divergencias()).anyMatch(d -> "I3".equals(d.regra()) && d.produtoId() != null && d.produtoId() == p);

        jdbc.update("delete from movimentacao_estoque where produto_id = ? and motivo = 'caminho que ignorou o lote'", p);
        jdbc.update("update produto set quantidade_estoque = 5 where id = ?", p);
        exigirConsistente(p);
    }
}
