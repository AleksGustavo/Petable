package br.com.tecnodreamer.petconnect.venda.web;

import br.com.tecnodreamer.petconnect.financeiro.application.FinanceiroService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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

    /** Contagens do que o financeiro e o caixa gravam: precisam ficar idênticas depois de uma falha. */
    private long lancamentos() {
        return jdbc.queryForObject("select count(*) from movimentacao_financeira", Long.class);
    }

    private long movimentosDeCaixa() {
        return jdbc.queryForObject("select count(*) from caixa_movimento", Long.class);
    }

    private String estadoDoCaixa() {
        return jdbc.queryForObject("select concat(count(*), ':', coalesce(sum(valor_total), 0)) from venda where status = 'CONCLUIDA'", String.class);
    }

    /** H-03: falha depois de o saldo dos lotes já ter sido debitado não deixa caixa nem financeiro inconsistentes. */
    @Test
    void h03_falhaDepoisDoDebitoDoSaldoNaoDeixaCaixaNemFinanceiroInconsistentes() throws Exception {
        long p = produtoControlado("10.00", "A:30:3", "B:90:7");
        String token = admin();
        long lancamentosAntes = lancamentos();
        long caixaAntes = movimentosDeCaixa();
        String vendasAntes = estadoDoCaixa();

        doThrow(new IllegalStateException("falha forçada depois do débito")).when(financeiro).registrarVenda(any(), any(), any());
        try {
            assertThatThrownBy(() -> vender(token, chave(), vendaJson("PIX", "0.00", "null", p, 8))).hasRootCauseMessage("falha forçada depois do débito");
        } finally {
            Mockito.reset(financeiro);
        }

        assertThat(lancamentos()).isEqualTo(lancamentosAntes);
        assertThat(movimentosDeCaixa()).isEqualTo(caixaAntes);
        assertThat(estadoDoCaixa()).isEqualTo(vendasAntes);
        assertThat(saldoLote(p, "A") + saldoLote(p, "B")).isEqualTo(10);
        assertThat(saldo(p)).isEqualTo(10);
        // A mesma venda, depois, gera exatamente um lançamento (e não dois): nada ficou pela metade.
        long venda = venderOk(token, vendaJson("PIX", "0.00", "null", p, 8));
        assertThat(lancamentos()).isEqualTo(lancamentosAntes + 1);
        assertThat(jdbc.queryForObject("select count(*) from movimentacao_financeira where chave_origem = ?", Integer.class, "VENDA:" + venda)).isEqualTo(1);
    }

    /** H-03b: falha ao criar a conta a pagar no recebimento desfaz a entrada dos lotes e a mudança de status da compra. */
    @Test
    void h03b_falhaNaContaAPagarDoRecebimentoDesfazAEntradaDosLotes() throws Exception {
        long p = produtoControlado("10.00");
        long fornecedor = criarFornecedor(unico("Fornecedor Falha"));
        long compra = criarCompra(fornecedor, String.valueOf(p), "10:7.00");
        long contasAntes = jdbc.queryForObject("select count(*) from conta_pagar", Long.class);

        doThrow(new IllegalStateException("falha forçada na conta a pagar")).when(financeiro).criarContaPagar(any(), any());
        try {
            assertThatThrownBy(() -> receberComLotes(compra, p, "R1:60:4", "R2:90:6")).hasRootCauseMessage("falha forçada na conta a pagar");
        } finally {
            Mockito.reset(financeiro);
        }

        assertThat(jdbc.queryForObject("select status from compra where id = ?", String.class, compra)).isEqualTo("PEDIDO");
        assertThat(jdbc.queryForObject("select count(*) from lote_estoque where produto_id = ?", Integer.class, p)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from item_compra_lote where lote_id is not null and item_compra_id in "
                + "(select id from item_compra where compra_id = ?)", Integer.class, compra)).isZero();
        assertThat(saldo(p)).isZero();
        assertThat(movimentacoes(p, "ENTRADA_COMPRA")).isZero();
        assertThat(jdbc.queryForObject("select count(*) from conta_pagar", Long.class)).isEqualTo(contasAntes);

        // Reenviado sem a falha, recebe uma vez só.
        receberComLotes(compra, p, "R1:60:4", "R2:90:6").andExpect(status().isOk());
        assertThat(saldo(p)).isEqualTo(10);
        assertThat(movimentacoes(p, "ENTRADA_COMPRA")).isEqualTo(2);
    }
}
