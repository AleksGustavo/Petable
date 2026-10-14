package br.com.tecnodreamer.petconnect.venda.web;

import br.com.tecnodreamer.petconnect.produto.application.EstoqueService;
import br.com.tecnodreamer.petconnect.produto.infrastructure.ProdutoRepository;
import br.com.tecnodreamer.petconnect.shared.error.ConflictException;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Etapa 03: o ciclo completo de produto com lote, pela API (regularização, recebimento, FEFO, cancelamento, ajustes). */
class LoteFluxoIT extends LoteIntegrationTest {

    @Autowired
    private EstoqueService estoqueService;
    @Autowired
    private ProdutoRepository produtos;
    @Autowired
    private TransactionTemplate tx;

    private String pix(long... itens) {
        return vendaJson("PIX", "0.00", "null", itens);
    }

    // ------------------------------------------------------------ regularização (D11)

    @Test
    void regularizacaoDistribuiOSaldoLegadoSemMudarOTotal() throws Exception {
        long p = produto("10.00", 10);
        acompanhar(p);

        ativarControle(p, false, null, "A:30:4", "B:90:6").andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].codigoLote").value("A"))
                .andExpect(jsonPath("$[0].saldo").value(4))
                .andExpect(jsonPath("$[0].vendavel").value(true));

        assertThat(saldo(p)).isEqualTo(10);
        assertThat(totalLotes(p)).isEqualTo(10);
        assertThat(movimentacoes(p, "REGULARIZACAO_LOTE")).isEqualTo(2);
        assertThat(jdbc.queryForObject("select controla_lote from produto where id = ?", Boolean.class, p)).isTrue();
        assertThat(acoesAuditadas("produto", p)).contains("ESTOQUE_REGULARIZADO");
        assertThat(acoesAuditadas("lote", loteId(p, "A"))).contains("LOTE_CRIADO");
    }

    @Test
    void regularizacaoComSomaDiferenteDoSaldoNaoGravaNada() throws Exception {
        long p = produto("10.00", 10);

        ativarControle(p, false, null, "A:30:4", "B:90:5").andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("select count(*) from lote_estoque where produto_id = ?", Integer.class, p)).isZero();
        assertThat(jdbc.queryForObject("select controla_lote from produto where id = ?", Boolean.class, p)).isFalse();
        assertThat(movimentacoes(p, "REGULARIZACAO_LOTE")).isZero();
        // Saldo > 0 com lista vazia também é soma diferente.
        ativarControle(p, false, null).andExpect(status().isConflict());
    }

    @Test
    void regularizacaoExigeValidadeSalvoPoliticaEDeixaPassarSaldoZero() throws Exception {
        long p = produto("10.00", 3);
        ativarControle(p, false, null, "SEM:-:3").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select permite_sem_validade from produto where id = ?", Boolean.class, p)).isFalse();

        ativarControle(p, true, 15, "SEM:-:3").andExpect(status().isCreated());
        acompanhar(p);
        assertThat(jdbc.queryForObject("select dias_bloqueio_validade from produto where id = ?", Integer.class, p)).isEqualTo(15);

        long vazio = produto("10.00", 0);
        acompanhar(vazio);
        ativarControle(vazio, false, null).andExpect(status().isCreated());
        ativarControle(vazio, false, null).andExpect(status().isConflict());
    }

    @Test
    void regularizacaoRecusaItemDeVendaCancelavelSemLote() throws Exception {
        long p = produto("10.00", 5);
        venderOk(admin(), pix(p, 1));

        ativarControle(p, false, null, "A:30:4").andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("select controla_lote from produto where id = ?", Boolean.class, p)).isFalse();
    }

    @Test
    void soAdminLigaOControleEVendasSoLe() throws Exception {
        long p = produto("10.00", 2);
        mvc.perform(post("/api/v1/produtos/" + p + "/controle-lote").with(bearer(vendas())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"lotes\": " + lotesJson("A:30:2") + "}")).andExpect(status().isForbidden());
        long c = produtoControlado("10.00", "A:30:2");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/produtos/" + c + "/lotes")
                .with(bearer(vendas()))).andExpect(status().isOk());
    }

    @Test
    void desligarOControleSoSemLotes() throws Exception {
        long comLote = produtoControlado("10.00", "A:30:2");
        postLote("/api/v1/produtos/" + comLote + "/controle-lote/desativar", "{}").andExpect(status().isConflict());

        long vazio = produto("10.00", 0);
        acompanhar(vazio);
        ativarControle(vazio, false, null).andExpect(status().isCreated());
        postLote("/api/v1/produtos/" + vazio + "/controle-lote/desativar", "{}").andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select controla_lote from produto where id = ?", Boolean.class, vazio)).isFalse();
    }

    // -------------------------------------------------------------------- recebimento

    @Test
    void recebimentoDeProdutoControladoExigeOsLotesESomaExata() throws Exception {
        long p = produtoControlado("10.00");
        long fornecedor = criarFornecedor(unico("Fornecedor Lote"));
        long compra = criarCompra(fornecedor, String.valueOf(p), "10:7.00");

        receber(compra, HOJE).andExpect(status().isBadRequest());
        receberComLotes(compra, p, "A:60:4", "B:120:5").andExpect(status().isBadRequest());
        receberComLotes(compra, p, "A:60:4", "A:60:6").andExpect(status().isBadRequest());
        receberComLotes(compra, p, "A:-5:10").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select status from compra where id = ?", String.class, compra)).isEqualTo("PEDIDO");
        assertThat(saldo(p)).isZero();

        receberComLotes(compra, p, "A:60:4", "B:120:6").andExpect(status().isOk());

        assertThat(saldo(p)).isEqualTo(10);
        assertThat(saldoLote(p, "A")).isEqualTo(4);
        assertThat(saldoLote(p, "B")).isEqualTo(6);
        assertThat(movimentosDoLote(p, "A", "ENTRADA_COMPRA")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from item_compra_lote where lote_id is not null and item_compra_id in "
                + "(select id from item_compra where compra_id = ?)", Integer.class, compra)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select compra_id from lote_estoque where produto_id = ? and codigo_lote = 'A'", Long.class, p))
                .isEqualTo(compra);
        assertThat(custo(p)).isEqualTo("7.00");

        // Repetir o recebimento (timeout na API): nada se duplica.
        receberComLotes(compra, p, "A:60:4", "B:120:6").andExpect(status().isConflict());
        assertThat(saldo(p)).isEqualTo(10);
        assertThat(movimentacoes(p, "ENTRADA_COMPRA")).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from conta_pagar where compra_id = ?", Integer.class, compra)).isEqualTo(1);
    }

    @Test
    void mesmoCodigoEmOutraCompraSomaAoLoteEValidadeDiferenteDesfazTudo() throws Exception {
        long p = produtoControlado("10.00");
        long fornecedor = criarFornecedor(unico("Fornecedor Lote"));
        long primeira = criarCompra(fornecedor, String.valueOf(p), "5:7.00");
        receberComLotes(primeira, p, "A:60:5").andExpect(status().isOk());

        long segunda = criarCompra(fornecedor, String.valueOf(p), "3:9.00");
        receberComLotes(segunda, p, "A:61:3").andExpect(status().isConflict());
        assertThat(saldo(p)).isEqualTo(5);
        assertThat(jdbc.queryForObject("select status from compra where id = ?", String.class, segunda)).isEqualTo("PEDIDO");

        receberComLotes(segunda, p, "A:60:3").andExpect(status().isOk());
        assertThat(saldoLote(p, "A")).isEqualTo(8);
        assertThat(jdbc.queryForObject("select count(*) from lote_estoque where produto_id = ?", Integer.class, p)).isEqualTo(1);
        // O custo do lote é o da primeira entrada; o do produto é a média ponderada.
        assertThat(jdbc.queryForObject("select custo_unitario from lote_estoque where produto_id = ?", String.class, p)).isEqualTo("7.00");
        assertThat(custo(p)).isEqualTo("7.75");
    }

    @Test
    void produtoSemControleNaoAceitaLotesNoRecebimento() throws Exception {
        long p = produto("10.00", 0);
        long fornecedor = criarFornecedor(unico("Fornecedor Lote"));
        long compra = criarCompra(fornecedor, String.valueOf(p), "5:7.00");

        receberComLotes(compra, p, "A:60:5").andExpect(status().isBadRequest());
        receber(compra, HOJE).andExpect(status().isOk());
        assertThat(saldo(p)).isEqualTo(5);
    }

    // ------------------------------------------------------------------------ FEFO

    @Test
    void vendaDeCincoConsomeTresDoLoteQueVenceAntesEDoisDoOutro() throws Exception {
        long p = produtoControlado("10.00", "TARDE:90:7", "CEDO:30:3");

        long venda = venderOk(admin(), pix(p, 5));

        assertThat(saldoLote(p, "CEDO")).isZero();
        assertThat(saldoLote(p, "TARDE")).isEqualTo(5);
        assertThat(saldo(p)).isEqualTo(5);
        assertThat(jdbc.queryForList("""
                select l.codigo_lote, il.quantidade from item_venda_lote il join lote_estoque l on l.id = il.lote_id
                join item_venda i on i.id = il.item_venda_id where i.venda_id = ? order by il.id""", venda))
                .extracting(m -> m.get("codigo_lote") + "=" + m.get("quantidade")).containsExactly("CEDO=3", "TARDE=2");
        assertThat(movimentosDoLote(p, "CEDO", "SAIDA_VENDA")).isEqualTo(1);
        assertThat(movimentosDoLote(p, "TARDE", "SAIDA_VENDA")).isEqualTo(1);
        assertThat(acoesAuditadas("venda", venda)).contains("VENDA_REALIZADA");
    }

    @Test
    void previaMostraAMesmaAlocacaoQueAVendaFaz() throws Exception {
        long p = produtoControlado("10.00", "TARDE:90:7", "CEDO:30:3");

        mvc.perform(post(URL + "/previa").with(bearer(admin())).contentType(MediaType.APPLICATION_JSON).content(pix(p, 5)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].disponivel").value(10))
                .andExpect(jsonPath("$.itens[0].suficiente").value(true))
                .andExpect(jsonPath("$.itens[0].lotes[0].codigoLote").value("CEDO"))
                .andExpect(jsonPath("$.itens[0].lotes[0].quantidade").value(3))
                .andExpect(jsonPath("$.itens[0].lotes[1].codigoLote").value("TARDE"))
                .andExpect(jsonPath("$.itens[0].lotes[1].quantidade").value(2));
        assertThat(saldo(p)).isEqualTo(10);
        assertThat(totalLotes(p)).isEqualTo(10);
    }

    @Test
    void loteVencidoOuBloqueadoNaoEntraNoFefoENaoHaExcecao() throws Exception {
        long p = produtoControlado("10.00", "VENCIDO:-1:5", "BOM:30:2", "BLOQ:40:4");
        postLote("/api/v1/produtos/" + p + "/lotes/" + loteId(p, "BLOQ") + "/bloquear", "{\"motivo\": \"Recolhimento do fabricante\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("BLOQUEADO"));

        // 11 unidades no total, mas só 2 vendáveis: nem o ADMIN vende o vencido ou o bloqueado.
        vender(admin(), chave(), pix(p, 3)).andExpect(status().isConflict()).andExpect(jsonPath("$.disponivel").value(2))
                .andExpect(jsonPath("$.solicitado").value(3));
        assertThat(saldo(p)).isEqualTo(11);
        assertThat(jdbc.queryForObject("select count(*) from item_venda_lote where lote_id = ?", Integer.class, loteId(p, "VENCIDO"))).isZero();

        venderOk(admin(), pix(p, 2));
        assertThat(saldoLote(p, "BOM")).isZero();
        assertThat(saldoLote(p, "VENCIDO")).isEqualTo(5);
        assertThat(saldoLote(p, "BLOQ")).isEqualTo(4);

        postLote("/api/v1/produtos/" + p + "/lotes/" + loteId(p, "BLOQ") + "/desbloquear", "{\"motivo\": \"Liberado pelo fabricante\"}")
                .andExpect(status().isOk());
        venderOk(admin(), pix(p, 4));
        assertThat(acoesAuditadas("lote", loteId(p, "BLOQ"))).contains("LOTE_BLOQUEADO", "LOTE_DESBLOQUEADO");
    }

    @Test
    void fronteiraDaValidadeVendeAteADataInclusive() throws Exception {
        long p = produtoControlado("10.00", "HOJE:0:2", "ONTEM:-1:2");

        venderOk(admin(), pix(p, 2));
        assertThat(saldoLote(p, "HOJE")).isZero();
        vender(admin(), chave(), pix(p, 1)).andExpect(status().isConflict());
        assertThat(saldoLote(p, "ONTEM")).isEqualTo(2);
    }

    @Test
    void margemDeBloqueioDoProdutoTiraDoFefoOLoteQueVenceEmBreve() throws Exception {
        long p = produto("10.00", 6);
        acompanhar(p);
        ativarControle(p, false, 30, "PERTO:10:2", "LONGE:100:4").andExpect(status().isCreated());

        vender(admin(), chave(), pix(p, 5)).andExpect(status().isConflict()).andExpect(jsonPath("$.disponivel").value(4));
        venderOk(admin(), pix(p, 4));
        assertThat(saldoLote(p, "PERTO")).isEqualTo(2);
    }

    @Test
    void mesmaChaveDeIdempotenciaBaixaUmaVezSo() throws Exception {
        long p = produtoControlado("10.00", "A:30:3", "B:90:7");
        String chave = chave();

        String primeira = vender(admin(), chave, pix(p, 5)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        MvcResult repetida = vender(admin(), chave, pix(p, 5)).andReturn();

        assertThat(repetida.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonPath.<Number>read(repetida.getResponse().getContentAsString(), "$.id").longValue())
                .isEqualTo(JsonPath.<Number>read(primeira, "$.id").longValue());
        assertThat(saldo(p)).isEqualTo(5);
        assertThat(movimentacoes(p, "SAIDA_VENDA")).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from item_venda_lote il join lote_estoque l on l.id = il.lote_id where l.produto_id = ?",
                Integer.class, p)).isEqualTo(2);
        long venda = JsonPath.<Number>read(primeira, "$.id").longValue();
        assertThat(jdbc.queryForObject("select count(*) from movimentacao_financeira where chave_origem = ?", Integer.class, "VENDA:" + venda))
                .isEqualTo(1);
    }

    // ---------------------------------------------------------------- cancelamento (D9)

    @Test
    void cancelarExigeODestinoDoEstoqueQuandoHaLote() throws Exception {
        long p = produtoControlado("10.00", "A:30:3", "B:90:7");
        long venda = venderOk(admin(), pix(p, 5));

        cancelarVenda(admin(), venda, null).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select status from venda where id = ?", String.class, venda)).isEqualTo("CONCLUIDA");
        assertThat(saldo(p)).isEqualTo(5);
    }

    @Test
    void retornarRecomporExatamenteOsLotesDeOrigem() throws Exception {
        long p = produtoControlado("10.00", "A:30:3", "B:90:7");
        long venda = venderOk(admin(), pix(p, 5));

        cancelarVenda(admin(), venda, "RETORNAR").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELADA"));

        assertThat(saldoLote(p, "A")).isEqualTo(3);
        assertThat(saldoLote(p, "B")).isEqualTo(7);
        assertThat(saldo(p)).isEqualTo(10);
        assertThat(movimentosDoLote(p, "A", "ESTORNO_VENDA")).isEqualTo(1);
        assertThat(movimentosDoLote(p, "B", "ESTORNO_VENDA")).isEqualTo(1);
        assertThat(movimentacoes(p, "PERDA")).isZero();
        assertThat(jdbc.queryForObject("select count(*) from movimentacao_financeira where chave_origem = ?", Integer.class, "VENDA_ESTORNO:" + venda))
                .isEqualTo(1);
        assertThat(acoesAuditadas("venda", venda)).contains("VENDA_CANCELADA");
        // Cancelar de novo: 409 e nada muda.
        cancelarVenda(admin(), venda, "RETORNAR").andExpect(status().isConflict());
        assertThat(saldo(p)).isEqualTo(10);
    }

    @Test
    void descartarGravaEstornoEPerdaNoMesmoLoteEEstornaODinheiro() throws Exception {
        long p = produtoControlado("10.00", "A:30:3", "B:90:7");
        long venda = venderOk(admin(), pix(p, 5));

        cancelarVenda(admin(), venda, "DESCARTAR").andExpect(status().isOk());

        // O saldo vendável não sobe: os lotes seguem como depois da venda; o rastro está completo.
        assertThat(saldoLote(p, "A")).isZero();
        assertThat(saldoLote(p, "B")).isEqualTo(5);
        assertThat(saldo(p)).isEqualTo(5);
        assertThat(movimentosDoLote(p, "A", "ESTORNO_VENDA")).isEqualTo(1);
        assertThat(movimentosDoLote(p, "A", "PERDA")).isEqualTo(1);
        assertThat(movimentosDoLote(p, "B", "PERDA")).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(distinct operacao_id) from movimentacao_estoque where produto_id = ? and tipo in ('ESTORNO_VENDA', 'PERDA')""",
                Integer.class, p)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from movimentacao_financeira where chave_origem = ?", Integer.class, "VENDA_ESTORNO:" + venda))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from movimentacao_estoque where venda_id = ? and tipo = 'PERDA'", Integer.class, venda))
                .isEqualTo(2);
    }

    @Test
    void cancelarVendaDeProdutoSemLoteContinuaComoAntes() throws Exception {
        long p = produto("10.00", 5);
        long venda = venderOk(admin(), pix(p, 2));

        cancelarVenda(admin(), venda, null).andExpect(status().isOk());

        assertThat(saldo(p)).isEqualTo(5);
        assertThat(movimentacoes(p, "ESTORNO_VENDA")).isEqualTo(1);
    }

    @Test
    void retornarParaLoteQueVenceuNaoOTornaVendavel() throws Exception {
        long p = produtoControlado("10.00", "A:1:3");
        long venda = venderOk(admin(), pix(p, 2));
        // O lote vence depois da venda (simulado movendo a validade para o passado).
        jdbc.update("update lote_estoque set validade = ? where produto_id = ?", HOJE.minusDays(1), p);

        cancelarVenda(admin(), venda, "RETORNAR").andExpect(status().isOk());

        assertThat(saldoLote(p, "A")).isEqualTo(3);
        vender(admin(), chave(), pix(p, 1)).andExpect(status().isConflict());
    }

    // ------------------------------------------------------- ajuste, perda, vencimento

    @Test
    void ajusteDeProdutoControladoExigeOLoteENuncaPassaDoSaldo() throws Exception {
        long p = produtoControlado("10.00", "A:30:5");
        String base = "/api/v1/produtos/" + p + "/ajustes-estoque";

        postLote(base, "{\"quantidade\": 1, \"motivo\": \"Sem lote informado\"}").andExpect(status().isBadRequest());
        postLote(base, "{\"quantidade\": -1, \"motivo\": \"Sem lote informado\", \"codigoLote\": \"A\"}").andExpect(status().isBadRequest());
        postLote(base, "{\"quantidade\": -6, \"motivo\": \"Passa do saldo\", \"loteId\": " + loteId(p, "A") + "}").andExpect(status().isConflict());
        assertThat(saldoLote(p, "A")).isEqualTo(5);

        postLote(base, "{\"quantidade\": -2, \"motivo\": \"Contagem física\", \"loteId\": " + loteId(p, "A") + "}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.codigoLote").value("A")).andExpect(jsonPath("$.loteSaldoResultante").value(3));
        postLote(base, "{\"quantidade\": 4, \"motivo\": \"Achado no depósito\", \"codigoLote\": \"NOVO\", \"validade\": \"" + HOJE.plusDays(200) + "\"}")
                .andExpect(status().isCreated());
        assertThat(saldoLote(p, "NOVO")).isEqualTo(4);
        assertThat(saldo(p)).isEqualTo(7);
        assertThat(acoesAuditadas("produto", p)).contains("ESTOQUE_AJUSTADO");
    }

    @Test
    void produtoSemControleRecusaDadosDeLoteNoAjuste() throws Exception {
        long p = produto("10.00", 3);
        postLote("/api/v1/produtos/" + p + "/ajustes-estoque", "{\"quantidade\": 1, \"motivo\": \"Ajuste com lote\", \"codigoLote\": \"X\", \"validade\": \""
                + HOJE.plusDays(5) + "\"}").andExpect(status().isBadRequest());
        ajustar(p, 1, "Ajuste normal sem lote").andExpect(status().isCreated());
    }

    @Test
    void perdaEVencimentoBaixamOLoteComMotivoEAuditoria() throws Exception {
        long p = produtoControlado("10.00", "OK:30:5", "VELHO:-2:4");
        String ok = "/api/v1/produtos/" + p + "/lotes/" + loteId(p, "OK");
        String velho = "/api/v1/produtos/" + p + "/lotes/" + loteId(p, "VELHO");

        postLote(ok + "/perdas", "{\"quantidade\": 6, \"motivo\": \"Quebra no transporte\"}").andExpect(status().isConflict());
        postLote(ok + "/perdas", "{\"quantidade\": 2, \"motivo\": \"Quebra no transporte\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tipo").value("PERDA")).andExpect(jsonPath("$.quantidade").value(-2));
        postLote(ok + "/vencimentos", "{\"quantidade\": 1, \"motivo\": \"Ainda dentro da validade\"}").andExpect(status().isConflict());
        postLote(velho + "/vencimentos", "{\"quantidade\": 4, \"motivo\": \"Lote vencido recolhido\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tipo").value("VENCIMENTO"));

        assertThat(saldoLote(p, "OK")).isEqualTo(3);
        assertThat(saldoLote(p, "VELHO")).isZero();
        assertThat(saldo(p)).isEqualTo(3);
        assertThat(acoesAuditadas("produto", p)).contains("ESTOQUE_PERDA", "ESTOQUE_VENCIMENTO");
    }

    @Test
    void loteDeOutroProdutoNaoEAcessivelPorEstaRota() throws Exception {
        long a = produtoControlado("10.00", "A:30:5");
        long b = produtoControlado("10.00", "B:30:5");
        postLote("/api/v1/produtos/" + a + "/lotes/" + loteId(b, "B") + "/perdas", "{\"quantidade\": 1, \"motivo\": \"Lote errado\"}")
                .andExpect(status().isNotFound());
        assertThat(saldoLote(b, "B")).isEqualTo(5);
    }

    // ------------------------------------------------- nenhum caminho ignora o lote

    @Test
    void servicoDeEstoqueSemLoteRecusaProdutoControlado() throws Exception {
        long p = produtoControlado("10.00", "A:30:5");
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> estoqueService.registrarSaidaVenda(
                produtos.travar(p).orElseThrow(), 1, null, adminId()))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> estoqueService.registrarEstornoVenda(
                produtos.travar(p).orElseThrow(), 1, null, adminId()))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> estoqueService.registrarEntradaCompra(
                produtos.travar(p).orElseThrow(), 1, new java.math.BigDecimal("1.00"), null, adminId()))).isInstanceOf(ConflictException.class);
        assertThat(saldoLote(p, "A")).isEqualTo(5);
        assertThat(saldo(p)).isEqualTo(5);
    }

    @Test
    void historicoMostraOLoteEOsSaldosDoLote() throws Exception {
        long p = produtoControlado("10.00", "A:30:5");
        venderOk(admin(), pix(p, 2));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/produtos/" + p + "/movimentacoes")
                        .param("tipo", "SAIDA_VENDA").with(bearer(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].codigoLote").value("A"))
                .andExpect(jsonPath("$.content[0].loteSaldoAnterior").value(5))
                .andExpect(jsonPath("$.content[0].loteSaldoResultante").value(3))
                .andExpect(jsonPath("$.content[0].quantidade").value(-2));
    }

    @Test
    void vendaMistaUsaLoteSoNoProdutoControlado() throws Exception {
        long controlado = produtoControlado("10.00", "A:30:5");
        long comum = produto("10.00", 5);

        long venda = venderOk(admin(), pix(comum, 2, controlado, 3));

        assertThat(saldo(comum)).isEqualTo(3);
        assertThat(saldoLote(controlado, "A")).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from item_venda_lote il join item_venda i on i.id = il.item_venda_id where i.venda_id = ?",
                Integer.class, venda)).isEqualTo(1);
        cancelarVenda(admin(), venda, "RETORNAR").andExpect(status().isOk());
        assertThat(saldo(comum)).isEqualTo(5);
        assertThat(saldoLote(controlado, "A")).isEqualTo(5);
    }
}
