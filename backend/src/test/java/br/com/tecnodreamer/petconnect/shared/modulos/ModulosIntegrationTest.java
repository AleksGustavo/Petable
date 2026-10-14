package br.com.tecnodreamer.petconnect.shared.modulos;

import br.com.tecnodreamer.petconnect.venda.web.VendaIntegrationTest;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Apoio dos ITs de módulos (etapa 07): negação uniforme e dados semeados direto no banco (o módulo que os criaria está desligado). */
abstract class ModulosIntegrationTest extends VendaIntegrationTest {

    /** 403 com o corpo uniforme, sem revelar qual módulo. */
    protected static void exigirIndisponivel(ResultActions r) throws Exception {
        r.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("MODULO_INDISPONIVEL"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("módulo"))));
    }

    protected ResultActions consultar(String url, String token) throws Exception {
        return mvc.perform(get(url).with(bearer(token)));
    }

    /** Saldo inicial consistente (I1) sem passar pela rota de ajuste, que pode estar desligada. */
    protected void semearSaldo(long produtoId, int saldo) {
        long admin = adminId();
        jdbc.update("update produto set quantidade_estoque = ? where id = ?", saldo, produtoId);
        jdbc.update("""
                insert into movimentacao_estoque (produto_id, tipo, quantidade, saldo_anterior, saldo_resultante, usuario_id, motivo)
                values (?, 'AJUSTE', ?, 0, ?, ?, 'saldo semeado pelo teste')""", produtoId, saldo, saldo, admin);
    }

    /** Produto com controle de lote e lotes semeados no banco: {@code "codigo:dias:saldo"}. */
    protected void semearLotes(long produtoId, String... lotes) {
        long admin = adminId();
        int total = 0;
        jdbc.update("update produto set controla_lote = true where id = ?", produtoId);
        for (String spec : lotes) {
            String[] p = spec.split(":");
            int saldo = Integer.parseInt(p[2]);
            jdbc.update("insert into lote_estoque (produto_id, codigo_lote, validade, saldo, data_recebimento, usuario_id) values (?, ?, ?, ?, ?, ?)",
                    produtoId, p[0], HOJE.plusDays(Long.parseLong(p[1])), saldo, HOJE, admin);
            long lote = jdbc.queryForObject("select id from lote_estoque where produto_id = ? and codigo_lote = ?", Long.class, produtoId, p[0]);
            jdbc.update("""
                    insert into movimentacao_estoque (produto_id, tipo, quantidade, saldo_anterior, saldo_resultante, usuario_id, lote_id,
                                                      lote_saldo_anterior, lote_saldo_resultante, motivo)
                    values (?, 'AJUSTE', ?, ?, ?, ?, ?, 0, ?, 'lote semeado pelo teste')""", produtoId, saldo, total, total + saldo, admin, lote, saldo);
            total += saldo;
        }
        jdbc.update("update produto set quantidade_estoque = ? where id = ?", total, produtoId);
    }

    protected static LocalDate hoje() {
        return HOJE;
    }
}
