package br.com.tecnodreamer.petconnect.venda.web;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Etapa 04: consulta de lotes, alertas derivados, rastreabilidade e dados que a tela precisa (papéis conferidos na API). */
class LoteConsultaIT extends LoteIntegrationTest {

    private String pix(long... itens) {
        return vendaJson("PIX", "0.00", "null", itens);
    }

    private String corpo(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString();
    }

    private long alertaLotes(String caminho) throws Exception {
        String json = corpo(mvc.perform(get("/api/v1/estoque/alertas").with(bearer(admin()))).andExpect(status().isOk()));
        return ((Number) JsonPath.read(json, caminho)).longValue();
    }

    // -------------------------------------------------------------------- consulta

    @Test
    void buscaPorCodigoDoLoteNomeDoProdutoECodigoDeBarras() throws Exception {
        long p = produtoControlado("10.00", "XYZ-" + System.nanoTime() % 100000 + ":30:4");
        String codigo = jdbc.queryForObject("select codigo_lote from lote_estoque where produto_id = ?", String.class, p);
        String nome = jdbc.queryForObject("select nome from produto where id = ?", String.class, p);
        String barras = jdbc.queryForObject("select codigo_barras from produto where id = ?", String.class, p);

        for (String busca : new String[] {codigo.toLowerCase(), nome, barras}) {
            mvc.perform(get("/api/v1/lotes").param("busca", busca).with(bearer(admin())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].codigoLote").value(codigo))
                    .andExpect(jsonPath("$.content[0].produtoNome").value(nome))
                    .andExpect(jsonPath("$.content[0].saldo").value(4))
                    .andExpect(jsonPath("$.content[0].vendavel").value(true));
        }
    }

    @Test
    void ordenaPorValidadeComSemValidadePorUltimoEPagina() throws Exception {
        long p = produto("10.00", 6);
        acompanhar(p);
        ativarControle(p, true, null, "TARDE:90:2", "SEM:-:1", "CEDO:10:3").andExpect(status().isCreated());
        String nome = jdbc.queryForObject("select nome from produto where id = ?", String.class, p);

        mvc.perform(get("/api/v1/lotes").param("produtoId", String.valueOf(p)).with(bearer(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].codigoLote").value("CEDO"))
                .andExpect(jsonPath("$.content[1].codigoLote").value("TARDE"))
                .andExpect(jsonPath("$.content[2].codigoLote").value("SEM"))
                .andExpect(jsonPath("$.content[2].validade").doesNotExist());
        mvc.perform(get("/api/v1/lotes").param("busca", nome).param("size", "2").param("page", "1").with(bearer(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void filtrosDeVencidoJanelaStatusESaldo() throws Exception {
        long p = produto("10.00", 9);
        acompanhar(p);
        ativarControle(p, false, null, "VELHO:-3:2", "PERTO:5:3", "LONGE:200:4").andExpect(status().isCreated());
        postLote("/api/v1/produtos/" + p + "/lotes/" + loteId(p, "LONGE") + "/bloquear", "{\"motivo\": \"Quarentena de teste\"}").andExpect(status().isOk());
        postLote("/api/v1/produtos/" + p + "/lotes/" + loteId(p, "VELHO") + "/vencimentos", "{\"quantidade\": 2, \"motivo\": \"Baixa do lote vencido\"}")
                .andExpect(status().isCreated());
        String base = "/api/v1/lotes?produtoId=" + p;

        mvc.perform(get(base).with(bearer(admin()))).andExpect(jsonPath("$.totalElements").value(2)); // VELHO zerado fica de fora
        mvc.perform(get(base + "&comSaldo=false").with(bearer(admin()))).andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get(base + "&comSaldo=false&vencido=true").with(bearer(admin())))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].codigoLote").value("VELHO"))
                .andExpect(jsonPath("$.content[0].vencido").value(true));
        mvc.perform(get(base + "&venceEmDias=30").with(bearer(admin())))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].codigoLote").value("PERTO"));
        mvc.perform(get(base + "&status=BLOQUEADO").with(bearer(admin())))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].codigoLote").value("LONGE"))
                .andExpect(jsonPath("$.content[0].vendavel").value(false));
        mvc.perform(get(base + "&vencido=true&venceEmDias=30").with(bearer(admin()))).andExpect(status().isBadRequest());
        mvc.perform(get(base + "&venceEmDias=-1").with(bearer(admin()))).andExpect(status().isBadRequest());
    }

    // --------------------------------------------------------------------- alertas

    @Test
    void alertasSaoDerivadosDoDadoRealEMudamSoQuandoOLoteMuda() throws Exception {
        long vencidosAntes = alertaLotes("$.vencidos.unidades");
        long faixa30Antes = alertaLotes("$.faixas[0].unidades");
        long faixa60Antes = alertaLotes("$.faixas[1].unidades");
        long faixa90Antes = alertaLotes("$.faixas[2].unidades");
        long bloqueadosAntes = alertaLotes("$.bloqueados.unidades");
        long semControleAntes = alertaLotes("$.produtosSemControle");

        long semControle = produto("10.00", 3); // saldo sem controle de lote
        long p = produtoControlado("10.00", "VELHO:-1:2", "L30:30:3", "L31:31:5", "L61:61:7", "L90:90:11", "L91:91:13");
        postLote("/api/v1/produtos/" + p + "/lotes/" + loteId(p, "L30") + "/bloquear", "{\"motivo\": \"Quarentena de teste\"}").andExpect(status().isOk());

        // 0–30: L30 (3). 31–60: L31 (5). 61–90: L61 e L90 (7 + 11). L91 fica fora das faixas.
        assertThat(alertaLotes("$.vencidos.unidades") - vencidosAntes).isEqualTo(2);
        assertThat(alertaLotes("$.faixas[0].unidades") - faixa30Antes).isEqualTo(3);
        assertThat(alertaLotes("$.faixas[1].unidades") - faixa60Antes).isEqualTo(5);
        assertThat(alertaLotes("$.faixas[2].unidades") - faixa90Antes).isEqualTo(18);
        assertThat(alertaLotes("$.bloqueados.unidades") - bloqueadosAntes).isEqualTo(3);
        assertThat(alertaLotes("$.produtosSemControle") - semControleAntes).isEqualTo(1);
        // Consultar de novo não muda nada: não há notificação nem estado gravado pelo alerta.
        assertThat(alertaLotes("$.vencidos.unidades") - vencidosAntes).isEqualTo(2);

        // Baixar o lote vencido tira o alerta; regularizar o produto sem controle tira o "sem controle".
        postLote("/api/v1/produtos/" + p + "/lotes/" + loteId(p, "VELHO") + "/vencimentos", "{\"quantidade\": 2, \"motivo\": \"Baixa do lote vencido\"}")
                .andExpect(status().isCreated());
        ativarControle(semControle, false, null, "REG:50:3").andExpect(status().isCreated());
        acompanhar(semControle);
        assertThat(alertaLotes("$.vencidos.unidades") - vencidosAntes).isZero();
        assertThat(alertaLotes("$.produtosSemControle") - semControleAntes).isZero();
    }

    @Test
    void alertasTraemHabilitadoEAsFaixasPadrao() throws Exception {
        mvc.perform(get("/api/v1/estoque/alertas").with(bearer(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.habilitado").value(true))
                .andExpect(jsonPath("$.faixas.length()").value(3))
                .andExpect(jsonPath("$.faixas[0].deDias").value(0))
                .andExpect(jsonPath("$.faixas[0].ateDias").value(30))
                .andExpect(jsonPath("$.faixas[1].deDias").value(31))
                .andExpect(jsonPath("$.faixas[2].ateDias").value(90))
                .andExpect(jsonPath("$.produtosAbaixoDoMinimo").isNumber());
    }

    // ---------------------------------------------------------------- permissões

    @Test
    void leituraDeLotesEAlertasSoParaAdminEVendas() throws Exception {
        long p = produtoControlado("10.00", "A:30:2");
        long lote = loteId(p, "A");
        for (String url : new String[] {"/api/v1/lotes", "/api/v1/estoque/alertas", "/api/v1/lotes/" + lote + "/rastreio"}) {
            mvc.perform(get(url).with(comPapel("SERVICO"))).andExpect(status().isForbidden());
            mvc.perform(get(url).with(comPapel("VENDAS"))).andExpect(status().isOk());
            mvc.perform(get(url).with(comPapel("ADMIN"))).andExpect(status().isOk());
        }
        mvc.perform(get("/api/v1/lotes")).andExpect(status().isUnauthorized());
    }

    @Test
    void acoesDeLoteSoParaAdmin() throws Exception {
        long p = produtoControlado("10.00", "A:30:5");
        String base = "/api/v1/produtos/" + p + "/lotes/" + loteId(p, "A");
        String vendas = vendas();
        for (String[] acao : new String[][] {
                {base + "/bloquear", "{\"motivo\": \"Tentativa sem permissão\"}"},
                {base + "/desbloquear", "{\"motivo\": \"Tentativa sem permissão\"}"},
                {base + "/perdas", "{\"quantidade\": 1, \"motivo\": \"Tentativa sem permissão\"}"},
                {base + "/vencimentos", "{\"quantidade\": 1, \"motivo\": \"Tentativa sem permissão\"}"},
                {"/api/v1/produtos/" + p + "/controle-lote/desativar", "{}"},
                {"/api/v1/produtos/" + p + "/ajustes-estoque", "{\"quantidade\": -1, \"motivo\": \"Tentativa sem permissão\", \"loteId\": " + loteId(p, "A") + "}"}}) {
            mvc.perform(post(acao[0]).with(bearer(vendas)).contentType(MediaType.APPLICATION_JSON).content(acao[1])).andExpect(status().isForbidden());
            mvc.perform(post(acao[0]).with(comPapel("SERVICO")).contentType(MediaType.APPLICATION_JSON).content(acao[1])).andExpect(status().isForbidden());
        }
        assertThat(saldoLote(p, "A")).isEqualTo(5);
        assertThat(jdbc.queryForObject("select status from lote_estoque where produto_id = ?", String.class, p)).isEqualTo("DISPONIVEL");
    }

    // ------------------------------------------------------------ rastreabilidade

    @Test
    void rastreioMostraEntradaSaidasEMovimentosSemDadoDeCliente() throws Exception {
        long p = produtoControlado("10.00");
        long fornecedor = criarFornecedor(unico("Fornecedor Rastreio"));
        long compra = criarCompra(fornecedor, String.valueOf(p), "10:7.00");
        receberComLotes(compra, p, "RAST:60:10").andExpect(status().isOk());
        long venda = venderOk(admin(), pix(p, 4));
        long cancelada = venderOk(admin(), pix(p, 2));
        cancelarVenda(admin(), cancelada, "RETORNAR").andExpect(status().isOk());

        mvc.perform(get("/api/v1/lotes/" + loteId(p, "RAST") + "/rastreio").with(bearer(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lote.codigoLote").value("RAST"))
                .andExpect(jsonPath("$.lote.saldo").value(6))
                .andExpect(jsonPath("$.entradas.length()").value(1))
                .andExpect(jsonPath("$.entradas[0].compraId").value(compra))
                .andExpect(jsonPath("$.entradas[0].quantidade").value(10))
                .andExpect(jsonPath("$.entradas[0].fornecedor.id").value(fornecedor))
                .andExpect(jsonPath("$.saidas.length()").value(2))
                .andExpect(jsonPath("$.saidas[0].vendaId").value(venda))
                .andExpect(jsonPath("$.saidas[0].statusVenda").value("CONCLUIDA"))
                .andExpect(jsonPath("$.saidas[1].statusVenda").value("CANCELADA"))
                .andExpect(jsonPath("$.saidas[0].cliente").doesNotExist())
                .andExpect(jsonPath("$.movimentos.length()").value(4))
                .andExpect(jsonPath("$.movimentos[0].tipo").value("ENTRADA_COMPRA"))
                .andExpect(jsonPath("$.movimentos[3].tipo").value("ESTORNO_VENDA"))
                .andExpect(jsonPath("$.totalMovimentos").value(4));
        mvc.perform(get("/api/v1/lotes/999999999/rastreio").with(bearer(admin()))).andExpect(status().isNotFound());
    }

    // --------------------------------------------- dados que as telas consomem

    @Test
    void detalhesDeVendaECompraTrazemOsLotes() throws Exception {
        long p = produtoControlado("10.00", "A:30:3", "B:90:7");
        long venda = venderOk(admin(), pix(p, 5));

        mvc.perform(get("/api/v1/vendas/" + venda).with(bearer(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].lotes.length()").value(2))
                .andExpect(jsonPath("$.itens[0].lotes[0].codigoLote").value("A"))
                .andExpect(jsonPath("$.itens[0].lotes[0].quantidade").value(3))
                .andExpect(jsonPath("$.itens[0].lotes[1].codigoLote").value("B"))
                .andExpect(jsonPath("$.itens[0].lotes[1].quantidade").value(2));

        long vazio = produtoControlado("10.00");
        long compra = criarCompra(criarFornecedor(unico("Fornecedor Detalhe")), String.valueOf(vazio), "4:5.00");
        mvc.perform(get("/api/v1/compras/" + compra).with(bearer(admin())))
                .andExpect(jsonPath("$.itens[0].controlaLote").value(true)).andExpect(jsonPath("$.itens[0].lotes.length()").value(0));
        receberComLotes(compra, vazio, "C1:40:1", "C2:80:3").andExpect(status().isOk());
        mvc.perform(get("/api/v1/compras/" + compra).with(bearer(admin())))
                .andExpect(jsonPath("$.itens[0].lotes.length()").value(2))
                .andExpect(jsonPath("$.itens[0].lotes[0].codigoLote").value("C1"))
                .andExpect(jsonPath("$.itens[0].lotes[1].quantidade").value(3));
    }

    @Test
    void produtoEBuscaRapidaInformamControleEQuantidadeVendavel() throws Exception {
        long p = produtoControlado("10.00", "VELHO:-1:5", "BOM:30:2");
        String nome = jdbc.queryForObject("select nome from produto where id = ?", String.class, p);

        mvc.perform(get("/api/v1/produtos/" + p).with(bearer(admin())))
                .andExpect(jsonPath("$.controlaLote").value(true))
                .andExpect(jsonPath("$.permiteSemValidade").value(false))
                .andExpect(jsonPath("$.quantidadeEstoque").value(7));
        mvc.perform(get("/api/v1/produtos/busca-rapida").param("q", nome).with(bearer(admin())))
                .andExpect(jsonPath("$[0].quantidadeEstoque").value(7))
                .andExpect(jsonPath("$[0].quantidadeVendavel").value(2))
                .andExpect(jsonPath("$[0].disponivel").value(true))
                .andExpect(jsonPath("$[0].controlaLote").value(true));

        postLote("/api/v1/produtos/" + p + "/lotes/" + loteId(p, "BOM") + "/bloquear", "{\"motivo\": \"Quarentena de teste\"}").andExpect(status().isOk());
        mvc.perform(get("/api/v1/produtos/busca-rapida").param("q", nome).with(bearer(admin())))
                .andExpect(jsonPath("$[0].quantidadeVendavel").value(0))
                .andExpect(jsonPath("$[0].disponivel").value(false));

        long comum = produto("10.00", 4);
        String nomeComum = jdbc.queryForObject("select nome from produto where id = ?", String.class, comum);
        mvc.perform(get("/api/v1/produtos/busca-rapida").param("q", nomeComum).with(bearer(admin())))
                .andExpect(jsonPath("$[0].quantidadeVendavel").value(4))
                .andExpect(jsonPath("$[0].controlaLote").value(false))
                .andExpect(jsonPath("$[0].disponivel").value(true));
    }
}
