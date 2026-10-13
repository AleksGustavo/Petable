package br.com.tecnodreamer.petconnect.relatorio.web;

import com.jayway.jsonpath.JsonPath;
import br.com.tecnodreamer.petconnect.shared.audit.EventoAuditoria;
import br.com.tecnodreamer.petconnect.venda.web.VendaIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Relatórios com dados controlados: cada teste insere fatos em datas próprias no
 * futuro (2031+), isoladas do resto da suíte, para conferir números exatos.
 */
class RelatorioControllerIT extends VendaIntegrationTest {

    private static final String URL = "/api/v1/relatorios";

    private long insert(String tabela, Map<String, Object> valores) {
        return new SimpleJdbcInsert(jdbc).withTableName(tabela).usingColumns(valores.keySet().toArray(String[]::new))
                .usingGeneratedKeyColumns("id").executeAndReturnKey(valores).longValue();
    }

    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> r = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            r.put((String) kv[i], kv[i + 1]);
        }
        return r;
    }

    private long venda(LocalDateTime quando, String status, String forma, String total, String desconto, long usuarioId) {
        Map<String, Object> v = m("data_venda", quando, "valor_total", new BigDecimal(total), "desconto", new BigDecimal(desconto),
                "forma_pagamento", forma, "status", status, "usuario_id", usuarioId);
        if ("CANCELADA".equals(status)) {
            v.put("cancelada_em", quando.plusHours(1));
            v.put("motivo_cancelamento", "Teste de relatório");
        }
        return insert("venda", v);
    }

    private void item(long vendaId, long produtoId, int quantidade, String preco) {
        insert("item_venda", m("venda_id", vendaId, "produto_id", produtoId, "quantidade", quantidade, "preco_unitario", new BigDecimal(preco)));
    }

    private ResultActions relatorio(String tipo, String papel, LocalDate inicio, LocalDate fim) throws Exception {
        return mvc.perform(get(URL + "/" + tipo).param("dataInicio", inicio.toString()).param("dataFim", fim.toString()).with(comPapel(papel)));
    }

    /** Cenário de vendas no dia {@code d}: 100 (PIX, admin) + 50 com 10 de desconto (DINHEIRO, outro) + 30 cancelada. */
    private long[] vendasDoDia(LocalDate d, long produtoA, long produtoB) throws Exception {
        long admin = adminId();
        long outro = criarUsuario(nomeUsuario(), "VENDAS");
        long v1 = venda(d.atTime(10, 0), "CONCLUIDA", "PIX", "100.00", "0.00", admin);
        item(v1, produtoA, 2, "50.00");
        long v2 = venda(d.atTime(15, 30), "CONCLUIDA", "DINHEIRO", "50.00", "10.00", outro);
        item(v2, produtoB, 3, "20.00");
        long v3 = venda(d.atTime(18, 0), "CANCELADA", "PIX", "30.00", "0.00", admin);
        item(v3, produtoA, 1, "30.00");
        return new long[] {v1, v2, v3};
    }

    @Test
    void vendasDeveSomarSoConcluidasComCanceladasSeparadas() throws Exception {
        LocalDate d = LocalDate.of(2031, 3, 10);
        vendasDoDia(d, produto("50.00", 0), produto("20.00", 0));

        relatorio("vendas", "ADMIN", d, d).andExpect(status().isOk())
                .andExpect(jsonPath("$.quantidade").value(2))
                .andExpect(jsonPath("$.bruto").value(160.0))
                .andExpect(jsonPath("$.descontos").value(10.0))
                .andExpect(jsonPath("$.liquido").value(150.0))
                .andExpect(jsonPath("$.ticketMedio").value(75.0))
                .andExpect(jsonPath("$.canceladas.quantidade").value(1))
                .andExpect(jsonPath("$.canceladas.valor").value(30.0));
    }

    @Test
    void vendasDevePreencherDiasSemMovimento() throws Exception {
        LocalDate d = LocalDate.of(2031, 4, 10);
        vendasDoDia(d, produto("50.00", 0), produto("20.00", 0));

        relatorio("vendas", "VENDAS", d.minusDays(1), d.plusDays(1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.porDia", hasSize(3)))
                .andExpect(jsonPath("$.porDia[0].quantidade").value(0))
                .andExpect(jsonPath("$.porDia[0].liquido").value(0.0))
                .andExpect(jsonPath("$.porDia[1].data").value(d.toString()))
                .andExpect(jsonPath("$.porDia[1].liquido").value(150.0))
                .andExpect(jsonPath("$.porDia[2].quantidade").value(0));
    }

    @Test
    void vendasPorFormaEOperador() throws Exception {
        LocalDate d = LocalDate.of(2031, 5, 10);
        vendasDoDia(d, produto("50.00", 0), produto("20.00", 0));

        relatorio("vendas", "ADMIN", d, d)
                .andExpect(jsonPath("$.porForma[0].chave").value("PIX"))
                .andExpect(jsonPath("$.porForma[0].liquido").value(100.0))
                .andExpect(jsonPath("$.porForma[1].chave").value("DINHEIRO"))
                .andExpect(jsonPath("$.porOperador[0].chave").value(ADMIN_USER))
                .andExpect(jsonPath("$.porOperador[0].quantidade").value(1))
                .andExpect(jsonPath("$.porOperador", hasSize(2)));
    }

    @Test
    void produtosMaisVendidosOrdenadoPorQuantidade() throws Exception {
        LocalDate d = LocalDate.of(2031, 6, 10);
        long a = produto("50.00", 0);
        long b = produto("20.00", 0);
        vendasDoDia(d, a, b);

        mvc.perform(get(URL + "/produtos-mais-vendidos").param("dataInicio", d.toString()).param("dataFim", d.toString())
                        .param("limite", "5").with(comPapel("VENDAS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(2)))
                .andExpect(jsonPath("$.itens[0].produtoId").value(b))
                .andExpect(jsonPath("$.itens[0].quantidade").value(3))
                .andExpect(jsonPath("$.itens[1].faturamento").value(100.0))
                .andExpect(jsonPath("$.total").value(160.0))
                .andExpect(jsonPath("$.itens[1].participacao").value(62.5));
    }

    @Test
    void curvaAbcDeveClassificarPorFaturamento() throws Exception {
        LocalDate d = LocalDate.of(2031, 7, 10);
        long admin = adminId();
        long v = venda(d.atTime(9, 0), "CONCLUIDA", "PIX", "1000.00", "0.00", admin);
        long[] ps = {produto("700.00", 0), produto("200.00", 0), produto("60.00", 0), produto("40.00", 0)};
        String[] precos = {"700.00", "200.00", "60.00", "40.00"};
        for (int i = 0; i < 4; i++) {
            item(v, ps[i], 1, precos[i]);
        }

        relatorio("curva-abc", "VENDAS", d, d).andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].classe").value("A"))
                .andExpect(jsonPath("$.itens[1].classe").value("A"))
                .andExpect(jsonPath("$.itens[2].classe").value("B"))
                .andExpect(jsonPath("$.itens[3].classe").value("C"))
                .andExpect(jsonPath("$.resumo[0].produtos").value(2))
                .andExpect(jsonPath("$.resumo[0].participacao").value(90.0))
                .andExpect(jsonPath("$.total").value(1000.0));
    }

    @Test
    void margemDeveUsarCustoMedioAtualComAviso() throws Exception {
        LocalDate d = LocalDate.of(2031, 8, 10);
        long p = produto("50.00", 0);
        jdbc.update("update produto set preco_custo = 30.00 where id = ?", p);
        long v = venda(d.atTime(11, 0), "CONCLUIDA", "PIX", "100.00", "0.00", adminId());
        item(v, p, 2, "50.00");

        relatorio("margem", "ADMIN", d, d).andExpect(status().isOk())
                .andExpect(jsonPath("$.receita").value(100.0))
                .andExpect(jsonPath("$.custoEstimado").value(60.0))
                .andExpect(jsonPath("$.margem").value(40.0))
                .andExpect(jsonPath("$.margemPercentual").value(40.0))
                .andExpect(jsonPath("$.aviso").value(org.hamcrest.Matchers.containsString("custo médio ATUAL")));
    }

    @Test
    void estoqueDeveCalcularValorGiroECobertura() throws Exception {
        LocalDate d = LocalDate.of(2031, 9, 1);
        long p = produto("50.00", 12);
        jdbc.update("update produto set preco_custo = 22.00 where id = ?", p);
        long v = venda(d.atTime(11, 0), "CONCLUIDA", "PIX", "1500.00", "0.00", adminId());
        item(v, p, 30, "50.00");

        String body = relatorio("estoque", "ADMIN", d, d.plusDays(29)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> linha = JsonPath.<java.util.List<Map<String, Object>>>read(body, "$.itens[?(@.produtoId == " + p + ")]").get(0);
        assertThat(new BigDecimal(linha.get("valorEstoque").toString())).isEqualByComparingTo("264.00");
        assertThat(((Number) linha.get("quantidadeVendida")).intValue()).isEqualTo(30);
        assertThat(new BigDecimal(linha.get("giro").toString())).isEqualByComparingTo("2.50");
        assertThat(new BigDecimal(linha.get("coberturaDias").toString())).isEqualByComparingTo("12.0");
    }

    @Test
    void servicosPorServicoEFuncionario() throws Exception {
        LocalDate d = LocalDate.of(2031, 10, 10);
        long pet = criarPet(criarCliente(unico("Tutor Rel")), unico("Pet Rel"), "Cão");
        long banho = jdbc.queryForObject("select id from servico where nome = 'Banho'", Long.class);
        long func = criarFuncionario(criarUsuario(nomeUsuario(), "SERVICO"), "Joana Relatório");
        insert("servico_realizado", m("pet_id", pet, "servico_id", banho, "funcionario_id", func, "data_servico", d.atTime(9, 0),
                "valor", new BigDecimal("55.00")));
        insert("servico_realizado", m("pet_id", pet, "servico_id", banho, "data_servico", d.atTime(14, 0), "valor", new BigDecimal("45.00")));

        relatorio("servicos", "ADMIN", d, d).andExpect(status().isOk())
                .andExpect(jsonPath("$.quantidade").value(2))
                .andExpect(jsonPath("$.total").value(100.0))
                .andExpect(jsonPath("$.porServico[0].chave").value("Banho"))
                .andExpect(jsonPath("$.porFuncionario[0].chave").value("Joana Relatório"))
                .andExpect(jsonPath("$.porFuncionario[1].chave").value("Sem funcionário"));
    }

    private long lancamento(LocalDate d, String tipo, String categoria, String valor, String chave, Long estornoDe) {
        Map<String, Object> l = m("tipo", tipo, "categoria", categoria, "descricao", "Lançamento relatório", "valor", new BigDecimal(valor),
                "data_movimentacao", d, "chave_origem", chave);
        if (estornoDe != null) {
            l.put("estorno_de_id", estornoDe);
        }
        return insert("movimentacao_financeira", l);
    }

    @Test
    void despesasDevemExcluirSangriaESuprimentoEDescontarEstornos() throws Exception {
        LocalDate d = LocalDate.of(2031, 11, 10);
        long despesa = lancamento(d, "SAIDA", "DESPESA_OPERACIONAL", "100.00", "MANUAL:" + UUID.randomUUID(), null);
        lancamento(d, "ENTRADA", "DESPESA_OPERACIONAL", "30.00", "MANUAL_ESTORNO:" + despesa, despesa);
        lancamento(d, "SAIDA", "COMPRA_MERCADORIA", "50.00", "CONTA_PAGAR:" + UUID.randomUUID(), null);
        lancamento(d, "SAIDA", "OUTROS", "20.00", "CAIXA_SANGRIA:" + UUID.randomUUID(), null);
        lancamento(d, "ENTRADA", "OUTROS", "15.00", "CAIXA_SUPRIMENTO:" + UUID.randomUUID(), null);
        lancamento(d, "SAIDA", "VENDA_PRODUTO", "40.00", "VENDA_ESTORNO:" + UUID.randomUUID(), null);

        relatorio("despesas", "ADMIN", d, d).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(120.0))
                .andExpect(jsonPath("$.porCategoria", hasSize(2)))
                .andExpect(jsonPath("$.porCategoria[0].categoria").value("DESPESA_OPERACIONAL"))
                .andExpect(jsonPath("$.porCategoria[0].valor").value(70.0))
                .andExpect(jsonPath("$.porCategoria[1].valor").value(50.0));
    }

    /**
     * Caixa FECHADO como o fechamento real grava: esperado, contado e diferença de TODAS as formas
     * (zerados) além da diferença total. Linhas incompletas quebram outros ITs que listam caixas
     * no mesmo banco compartilhado (CaixaResponse soma os valores por forma).
     */
    private void caixaFechado(long usuarioId, LocalDateTime fechadoEm, String diferenca) {
        Map<String, Object> cols = new LinkedHashMap<>();
        cols.put("usuario_id", usuarioId);
        cols.put("status", "FECHADO");
        cols.put("aberto_em", fechadoEm.minusHours(8));
        cols.put("fundo_abertura", BigDecimal.ZERO);
        cols.put("fechado_em", fechadoEm);
        for (String prefixo : new String[] {"esperado_", "contado_", "diferenca_"}) {
            for (String forma : new String[] {"dinheiro", "cartao_credito", "cartao_debito", "pix", "transferencia"}) {
                cols.put(prefixo + forma, BigDecimal.ZERO);
            }
        }
        cols.put("diferenca_total", new BigDecimal(diferenca));
        insert("caixa", cols);
    }

    @Test
    void caixasFechadosNoPeriodoPorOperador() throws Exception {
        LocalDate d = LocalDate.of(2031, 12, 10);
        long joana = criarUsuario("rel" + Long.toString(System.nanoTime(), 36), "VENDAS");
        caixaFechado(joana, d.atTime(18, 0), "-5.00");
        caixaFechado(joana, d.atTime(19, 0), "-2.50");
        caixaFechado(adminId(), d.atTime(20, 0), "1.00");
        caixaFechado(adminId(), d.plusDays(1).atTime(8, 0), "-100.00");

        relatorio("caixas", "ADMIN", d, d).andExpect(status().isOk())
                .andExpect(jsonPath("$.quantidade").value(3))
                .andExpect(jsonPath("$.diferencaTotal").value(-6.5))
                .andExpect(jsonPath("$.porOperador[0].caixas").value(2))
                .andExpect(jsonPath("$.porOperador[0].diferenca").value(-7.5))
                .andExpect(jsonPath("$.porOperador[0].faltas").value(2))
                .andExpect(jsonPath("$.porOperador[1].sobras").value(1))
                .andExpect(jsonPath("$.caixas", hasSize(3)));
    }

    @Test
    void contasPagasNoPeriodo() throws Exception {
        LocalDate d = LocalDate.of(2032, 1, 10);
        long admin = adminId();
        for (String valor : new String[] {"40.00", "60.00"}) {
            insert("conta_pagar", m("descricao", "Conta relatório", "valor_original", new BigDecimal(valor), "saldo", BigDecimal.ZERO,
                    "data_vencimento", d, "status", "PAGA", "paga_em", d.atTime(10, 0), "forma_pagamento", "PIX",
                    "usuario_pagamento_id", admin));
        }

        relatorio("contas", "ADMIN", d, d).andExpect(status().isOk())
                .andExpect(jsonPath("$.pagas.quantidade").value(2))
                .andExpect(jsonPath("$.pagas.valor").value(100.0))
                .andExpect(jsonPath("$.itensPagos", hasSize(2)))
                .andExpect(jsonPath("$.vencidasEmAberto.quantidade").isNumber());
    }

    @Test
    void periodoInvalidoDeveRetornar400() throws Exception {
        LocalDate d = LocalDate.of(2031, 3, 10);
        relatorio("vendas", "ADMIN", d, d.minusDays(1)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.dataFim").exists());
        relatorio("vendas", "ADMIN", d, d.plusDays(366)).andExpect(status().isBadRequest());
        mvc.perform(get(URL + "/vendas").with(comPapel("ADMIN"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.dataInicio").exists());
        mvc.perform(get(URL + "/inexistente/csv").param("dataInicio", d.toString()).param("dataFim", d.toString())
                .with(comPapel("ADMIN"))).andExpect(status().isNotFound());
    }

    @Test
    void vendasPodeVerVendasMasNaoMargem() throws Exception {
        LocalDate d = LocalDate.of(2031, 3, 10);
        relatorio("vendas", "VENDAS", d, d).andExpect(status().isOk());
        relatorio("curva-abc", "VENDAS", d, d).andExpect(status().isOk());
        for (String tipo : new String[] {"margem", "estoque", "servicos", "despesas", "caixas", "contas"}) {
            relatorio(tipo, "VENDAS", d, d).andExpect(status().isForbidden());
            relatorio(tipo + "/csv", "VENDAS", d, d).andExpect(status().isForbidden());
        }
        relatorio("vendas/csv", "VENDAS", d, d).andExpect(status().isOk());
    }

    @Test
    void servicoNaoAcessaRelatorios() throws Exception {
        LocalDate d = LocalDate.of(2031, 3, 10);
        relatorio("vendas", "SERVICO", d, d).andExpect(status().isForbidden());
        relatorio("servicos", "SERVICO", d, d).andExpect(status().isForbidden());
        relatorio("vendas/csv", "SERVICO", d, d).andExpect(status().isForbidden());
    }

    @Test
    void csvDeveTerBomCabecalhoEFormatoBrasileiro() throws Exception {
        LocalDate d = LocalDate.of(2032, 2, 10);
        vendasDoDia(d, produto("50.00", 0), produto("20.00", 0));

        byte[] b = relatorio("vendas/csv", "ADMIN", d, d).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/csv")))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"relatorio-vendas-" + d + "-" + d + ".csv\""))
                .andReturn().getResponse().getContentAsByteArray();
        String csv = new String(b, StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFData;Vendas;Faturamento líquido (R$)\r\n");
        assertThat(csv).contains("10/02/2032;2;150,00\r\n").endsWith("Total;2;150,00\r\n");
    }

    @Test
    void exportacaoDeveSerAuditada() throws Exception {
        LocalDate d = LocalDate.of(2032, 3, 10);
        long antes = auditoria.findByEntidadeAndEntidadeId("relatorio", "despesas", Pageable.unpaged()).getTotalElements();

        relatorio("despesas/csv", "ADMIN", d, d).andExpect(status().isOk());

        var eventos = auditoria.findByEntidadeAndEntidadeId("relatorio", "despesas", Pageable.unpaged()).map(EventoAuditoria::acao).toList();
        assertThat(eventos).hasSize((int) antes + 1).contains("RELATORIO_EXPORTADO");
        // Consultar em tela não audita.
        relatorio("despesas", "ADMIN", d, d).andExpect(status().isOk());
        assertThat(auditoria.findByEntidadeAndEntidadeId("relatorio", "despesas", Pageable.unpaged()).getTotalElements()).isEqualTo(antes + 1);
    }
}
