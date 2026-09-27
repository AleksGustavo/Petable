package br.com.tecnodreamer.petconnect.financeiro.web;

import br.com.tecnodreamer.petconnect.financeiro.application.FinanceiroBackfill;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Backfill (DA-18). O "histórico" é simulado apagando, só no banco de teste, o que a
 * etapa 06 grava hoje (como era antes da V8); o backfill tem de recriar sem duplicar.
 */
class FinanceiroBackfillIT extends FinanceiroIntegrationTest {

    @Autowired
    FinanceiroBackfill backfill;

    private long vendaSemLancamento() throws Exception {
        long p = produto("20.00", 5);
        long venda = venderOk(admin(), vendaJson("PIX", "0", "null", p, 1));
        jdbc.update("delete from movimentacao_financeira where venda_id = ?", venda);
        return venda;
    }

    @Test
    void deveCriarHistorico() throws Exception {
        long venda = vendaSemLancamento();

        FinanceiroBackfill.Resultado r = backfill.executar();

        assertThat(r.vendas()).isGreaterThanOrEqualTo(1);
        var l = jdbc.queryForMap("select tipo, categoria, forma_pagamento, caixa_id, observacoes from movimentacao_financeira "
                + "where chave_origem = ?", "VENDA:" + venda);
        assertThat(l).containsEntry("tipo", "ENTRADA").containsEntry("categoria", "VENDA_PRODUTO").containsEntry("forma_pagamento", "PIX");
        assertThat(l.get("caixa_id")).isNull();
    }

    @Test
    void segundaExecucaoNaoDeveDuplicar() throws Exception {
        long venda = vendaSemLancamento();
        backfill.executar();

        FinanceiroBackfill.Resultado segunda = backfill.executar();

        assertThat(segunda.vendas()).isZero();
        assertThat(segunda.estornos()).isZero();
        assertThat(segunda.contas()).isZero();
        assertThat(lancamentos("venda_id = ?", venda)).isEqualTo(1);
    }

    @Test
    void vendaCanceladaDeveGerarOriginalEEstorno() throws Exception {
        long p = produto("20.00", 5);
        String token = admin();
        long venda = venderOk(token, vendaJson("PIX", "0", "null", p, 1));
        cancelar(token, venda, "Cancelada antes da V8").andExpect(status().isOk());
        jdbc.update("delete from movimentacao_financeira where chave_origem = ?", "VENDA_ESTORNO:" + venda);
        jdbc.update("delete from movimentacao_financeira where chave_origem = ?", "VENDA:" + venda);

        backfill.executar();

        Long entrada = jdbc.queryForObject("select id from movimentacao_financeira where chave_origem = ?", Long.class, "VENDA:" + venda);
        var estorno = jdbc.queryForMap("select tipo, estorno_de_id from movimentacao_financeira where chave_origem = ?",
                "VENDA_ESTORNO:" + venda);
        assertThat(estorno).containsEntry("tipo", "SAIDA");
        assertThat(((Number) estorno.get("estorno_de_id")).longValue()).isEqualTo(entrada);
    }

    @Test
    void compraRecebidaDeveGerarConta() throws Exception {
        long conta = compraRecebida("2:10.00");
        Long compra = jdbc.queryForObject("select compra_id from conta_pagar where id = ?", Long.class, conta);
        jdbc.update("delete from conta_pagar where id = ?", conta);

        backfill.executar();

        var nova = jdbc.queryForMap("select status, data_vencimento, valor_original from conta_pagar where compra_id = ?", compra);
        assertThat(nova).containsEntry("status", "ABERTA");
        assertThat(nova.get("data_vencimento").toString()).isEqualTo(HOJE.toString());
        assertThat(new java.math.BigDecimal(nova.get("valor_original").toString())).isEqualByComparingTo("20.00");
    }
}
