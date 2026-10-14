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

    @Test
    void ajusteVendaECancelamentoSemLoteFuncionamComoAntes() throws Exception {
        long p = produto("10.00", 5);

        long venda = venderOk(admin(), vendaJson("PIX", "0.00", "null", p, 2));
        assertThat(saldo(p)).isEqualTo(3);
        cancelar(admin(), venda, "Cancelamento sem lote").andExpect(status().isOk());
        assertThat(saldo(p)).isEqualTo(5);
    }
}
