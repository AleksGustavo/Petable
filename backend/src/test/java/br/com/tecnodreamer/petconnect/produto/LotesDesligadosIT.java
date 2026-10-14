package br.com.tecnodreamer.petconnect.produto;

import br.com.tecnodreamer.petconnect.produto.application.LotesProperties;
import br.com.tecnodreamer.petconnect.venda.web.VendaIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Com a flag desligada (o padrão de produção), nada de lote pode ser ligado nem movimentado, e o resto segue igual. */
@TestPropertySource(properties = "petconnect.estoque.lotes.habilitado=false")
class LotesDesligadosIT extends VendaIntegrationTest {

    @Autowired
    LotesProperties propriedades;

    @Test
    void controleDeLoteNaoLigaComAFlagDesligada() throws Exception {
        assertThat(propriedades.habilitado()).isFalse();
        long p = produto("10.00", 4);

        mvc.perform(post("/api/v1/produtos/" + p + "/controle-lote").with(bearer(admin())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"lotes\": [{\"codigoLote\": \"A\", \"validade\": \"" + HOJE.plusDays(30) + "\", \"quantidade\": 4}]}"))
                .andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("select controla_lote from produto where id = ?", Boolean.class, p)).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from lote_estoque where produto_id = ?", Integer.class, p)).isZero();
    }

    /**
     * Reversão operacional (N1): a flag é desligada com produto já controlado. Telas e novas ativações ficam bloqueadas, mas o
     * produto controlado continua vendendo por lote e aceitando ajuste, perda e bloqueio: o estoque não pode ficar sem correção.
     */
    @Test
    void produtoJaControladoSegueIntegroComAFlagDesligada() throws Exception {
        long p = produto("10.00", 0);
        jdbc.update("update produto set controla_lote = true, quantidade_estoque = 5 where id = ?", p);
        jdbc.update("insert into lote_estoque (produto_id, codigo_lote, validade, saldo, data_recebimento, usuario_id) values (?, 'OFF-1', ?, 5, ?, ?)",
                p, HOJE.plusDays(30), HOJE, jdbc.queryForObject("select id from usuario where username = ?", Long.class, ADMIN_USER));
        long lote = jdbc.queryForObject("select id from lote_estoque where produto_id = ?", Long.class, p);
        jdbc.update("""
                insert into movimentacao_estoque (produto_id, tipo, quantidade, saldo_anterior, saldo_resultante, usuario_id, lote_id, lote_saldo_anterior, lote_saldo_resultante)
                values (?, 'AJUSTE', 5, 0, 5, ?, ?, 0, 5)""", p, jdbc.queryForObject("select id from usuario where username = ?", Long.class, ADMIN_USER), lote);

        // Venda FEFO, ajuste, perda e bloqueio por lote funcionam (a flag não é a verdade do produto).
        venderOk(admin(), vendaJson("PIX", "0.00", "null", p, 2));
        mvc.perform(post("/api/v1/produtos/" + p + "/ajustes-estoque").with(bearer(admin())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantidade\": -1, \"motivo\": \"Contagem com a flag desligada\", \"loteId\": " + lote + "}")).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/produtos/" + p + "/lotes/" + lote + "/perdas").with(bearer(admin())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantidade\": 1, \"motivo\": \"Quebra com a flag desligada\"}")).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/produtos/" + p + "/lotes/" + lote + "/bloquear").with(bearer(admin())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\": \"Quarentena com a flag desligada\"}")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select saldo from lote_estoque where id = ?", Integer.class, lote)).isEqualTo(1);
        assertThat(saldo(p)).isEqualTo(1);
        // A venda de produto sem lote, no mesmo banco, segue igual; e ligar o controle de outro produto continua recusado.
        long outro = produto("10.00", 2);
        mvc.perform(post("/api/v1/produtos/" + outro + "/controle-lote").with(bearer(admin())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"lotes\": [{\"codigoLote\": \"X\", \"validade\": \"" + HOJE.plusDays(30) + "\", \"quantidade\": 2}]}")).andExpect(status().isConflict());
        // Sem dado nenhum apagado: o livro-razão do lote tem a entrada, a venda, o ajuste e a perda.
        assertThat(jdbc.queryForObject("select count(*) from movimentacao_estoque where lote_id = ?", Integer.class, lote)).isEqualTo(4);
    }

    @Test
    void ajusteVendaECancelamentoSemLoteFuncionamComoAntes() throws Exception {
        long p = produto("10.00", 5);

        long venda = venderOk(admin(), vendaJson("PIX", "0.00", "null", p, 2));
        assertThat(saldo(p)).isEqualTo(3);
        cancelar(admin(), venda, "Cancelamento sem lote").andExpect(status().isOk());
        assertThat(saldo(p)).isEqualTo(5);
    }
}
