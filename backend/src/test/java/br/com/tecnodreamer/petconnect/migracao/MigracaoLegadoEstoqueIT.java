package br.com.tecnodreamer.petconnect.migracao;

import br.com.tecnodreamer.petconnect.migracao.application.ResultadoEntidade;
import br.com.tecnodreamer.petconnect.migracao.application.ResultadoMigracao;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MigracaoLegadoEstoqueIT extends MigracaoIntegrationTest {

    private int saldo(long produtoLegado) {
        return jdbc.queryForObject("SELECT quantidade_estoque FROM produto WHERE id = ?", Integer.class,
                idNovo("produto", produtoLegado));
    }

    @Test
    void saldoFinalDeveConferirComLegado() {
        assertThat(List.of(saldo(1), saldo(2), saldo(3), saldo(4))).containsExactly(10, 0, 0, 7);
        assertThat(jdbc.queryForObject("SELECT motivo FROM movimentacao_estoque WHERE produto_id = ?", String.class,
                idNovo("produto", 1))).isEqualTo("Saldo inicial migrado do sistema antigo");
    }

    @Test
    void saldoNegativoDeveVirarZero() {
        assertThat(saldo(3)).isZero();
        assertThat(motivo("produto_estoque", 3)).isEqualTo("SALDO_NEGATIVO");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimentacao_estoque WHERE produto_id = ?", Long.class,
                idNovo("produto", 3))).isZero();
    }

    @Test
    void vendaHistoricaNaoDeveGerarSaidaVenda() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimentacao_estoque WHERE venda_id IN (?, ?)", Long.class,
                idNovo("venda", 1), idNovo("venda", 4))).isZero();
    }

    @Test
    void compraHistoricaNaoDeveGerarEntradaCompra() {
        Long compra = idNovo("compra", 1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimentacao_estoque WHERE compra_id = ?", Long.class, compra))
                .isZero();
        // Sem evidência de dívida no legado: nenhuma conta a pagar, nem pelo backfill.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM conta_pagar WHERE compra_id = ?", Long.class, compra)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM compra WHERE id = ?", String.class, compra)).isEqualTo("RECEBIDO");
    }
}
