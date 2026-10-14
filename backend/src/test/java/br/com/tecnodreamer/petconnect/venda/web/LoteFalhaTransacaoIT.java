package br.com.tecnodreamer.petconnect.venda.web;

import br.com.tecnodreamer.petconnect.financeiro.application.FinanceiroService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Falha forçada no meio da transação: a venda já alocou os lotes e gravou os movimentos quando o lançamento financeiro
 * quebra. Tudo precisa voltar: lote, produto, alocação, movimento, venda e financeiro (DA-05, D15).
 */
class LoteFalhaTransacaoIT extends LoteIntegrationTest {

    @MockitoSpyBean
    FinanceiroService financeiro;

    @Autowired
    br.com.tecnodreamer.petconnect.venda.application.VendaService vendaService;

    @Test
    void falhaNoFinanceiroDesfazAlocacaoMovimentosEBaixaDosLotes() throws Exception {
        long p = produtoControlado("10.00", "A:30:3", "B:90:7");
        String token = admin();
        long vendasAntes = jdbc.queryForObject("select count(*) from venda", Long.class);

        doThrow(new IllegalStateException("falha forçada no financeiro")).when(financeiro).registrarVenda(any(), any(), any());
        try {
            assertThatThrownBy(() -> vender(token, chave(), vendaJson("PIX", "0.00", "null", p, 5))).hasRootCauseMessage("falha forçada no financeiro");
        } finally {
            Mockito.reset(financeiro);
        }

        assertThat(saldoLote(p, "A")).isEqualTo(3);
        assertThat(saldoLote(p, "B")).isEqualTo(7);
        assertThat(saldo(p)).isEqualTo(10);
        assertThat(movimentacoes(p, "SAIDA_VENDA")).isZero();
        assertThat(jdbc.queryForObject("select count(*) from item_venda_lote il join lote_estoque l on l.id = il.lote_id where l.produto_id = ?",
                Integer.class, p)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from venda", Long.class)).isEqualTo(vendasAntes);

        // Sem a falha, a mesma venda passa: nada ficou preso.
        venderOk(token, vendaJson("PIX", "0.00", "null", p, 5));
        assertThat(saldo(p)).isEqualTo(5);
    }

    @Test
    void falhaNoEstornoFinanceiroDesfazOCancelamentoComDestino() throws Exception {
        long p = produtoControlado("10.00", "A:30:3", "B:90:7");
        String token = admin();
        long venda = venderOk(token, vendaJson("PIX", "0.00", "null", p, 5));

        doThrow(new IllegalStateException("falha forçada no estorno")).when(financeiro).registrarEstornoVenda(any(), any(), any(), any());
        try {
            assertThatThrownBy(() -> cancelarVenda(token, venda, "DESCARTAR")).hasRootCauseMessage("falha forçada no estorno");
        } finally {
            Mockito.reset(financeiro);
        }

        assertThat(jdbc.queryForObject("select status from venda where id = ?", String.class, venda)).isEqualTo("CONCLUIDA");
        assertThat(saldoLote(p, "A")).isZero();
        assertThat(saldoLote(p, "B")).isEqualTo(5);
        assertThat(movimentacoes(p, "ESTORNO_VENDA")).isZero();
        assertThat(movimentacoes(p, "PERDA")).isZero();

        cancelarVenda(token, venda, "RETORNAR").andExpect(status().isOk());
        assertThat(saldoLote(p, "A")).isEqualTo(3);
    }
}
