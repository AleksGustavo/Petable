package br.com.tecnodreamer.petconnect.shared.modulos;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Instalação com {@code produtos, estoque, caixa, pdv} e SEM {@code lotes}, com um produto que já controla lote. Desligar o acesso
 * gerencial a lotes não desliga o FEFO: a venda continua alocando pelo lote que vence primeiro (serviço interno), e o cancelamento
 * continua exigindo o destino do estoque. Só as rotas do módulo (consulta, alertas, controle, bloqueio, perda) somem.
 */
@TestPropertySource(properties = "petconnect.modulos=produtos,estoque,caixa,pdv")
class ModulosSemLotesIT extends ModulosIntegrationTest {

    @Test
    void fefoObrigatorioContinuaNaVendaMesmoSemOModuloDeLotes() throws Exception {
        long p = produto("10.00", 0);
        semearLotes(p, "TARDE:90:7", "CEDO:30:3", "VENCIDO:-1:5");

        long venda = venderOk(admin(), vendaJson("PIX", "0.00", "null", p, 5));

        // 3 do lote que vence antes + 2 do outro; o vencido nunca é usado.
        assertThat(jdbc.queryForObject("select saldo from lote_estoque where produto_id = ? and codigo_lote = 'CEDO'", Integer.class, p)).isZero();
        assertThat(jdbc.queryForObject("select saldo from lote_estoque where produto_id = ? and codigo_lote = 'TARDE'", Integer.class, p)).isEqualTo(5);
        assertThat(jdbc.queryForObject("select saldo from lote_estoque where produto_id = ? and codigo_lote = 'VENCIDO'", Integer.class, p)).isEqualTo(5);
        assertThat(jdbc.queryForObject("""
                select count(*) from item_venda_lote il join item_venda i on i.id = il.item_venda_id where i.venda_id = ?""", Integer.class, venda)).isEqualTo(2);
        // Sem saldo vendável, a venda é recusada pela regra interna (não pelo módulo).
        vender(admin(), java.util.UUID.randomUUID().toString(), vendaJson("PIX", "0.00", "null", p, 8)).andExpect(status().isConflict());
    }

    @Test
    void cancelamentoDeVendaComLoteContinuaExigindoODestinoEORecompoeOLoteOriginal() throws Exception {
        long p = produto("10.00", 0);
        semearLotes(p, "A:60:6");
        long venda = venderOk(admin(), vendaJson("PIX", "0.00", "null", p, 4));

        cancelar(admin(), venda, "Sem informar o destino do estoque").andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/vendas/" + venda + "/cancelar").with(bearer(admin())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\": \"Cliente desistiu\", \"destinoEstoque\": \"RETORNAR\"}")).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select saldo from lote_estoque where produto_id = ?", Integer.class, p)).isEqualTo(6);
    }

    @Test
    void asRotasDoModuloDeLotesFicamIndisponiveisParaTodosOsPapeis() throws Exception {
        long p = produto("10.00", 0);
        semearLotes(p, "A:60:6");
        long lote = jdbc.queryForObject("select id from lote_estoque where produto_id = ?", Long.class, p);
        String base = "/api/v1/produtos/" + p;
        String motivo = "{\"motivo\": \"Tentativa sem o módulo\", \"quantidade\": 1}";

        for (String token : new String[] {admin(), vendas()}) {
            exigirIndisponivel(consultar("/api/v1/lotes", token));
            exigirIndisponivel(consultar("/api/v1/lotes/" + lote + "/rastreio", token));
            exigirIndisponivel(consultar("/api/v1/estoque/alertas", token));
            exigirIndisponivel(consultar(base + "/lotes", token));
            for (String acao : new String[] {base + "/controle-lote", base + "/controle-lote/desativar", base + "/lotes/" + lote + "/bloquear",
                    base + "/lotes/" + lote + "/desbloquear", base + "/lotes/" + lote + "/perdas", base + "/lotes/" + lote + "/vencimentos"}) {
                exigirIndisponivel(mvc.perform(post(acao).with(bearer(token)).contentType(MediaType.APPLICATION_JSON).content(motivo)));
            }
        }
        assertThat(jdbc.queryForObject("select status from lote_estoque where id = ?", String.class, lote)).isEqualTo("DISPONIVEL");
        assertThat(jdbc.queryForObject("select saldo from lote_estoque where id = ?", Integer.class, lote)).isEqualTo(6);
    }

    @Test
    void ajusteComLoteContinuaPeloModuloDeEstoqueEMantemAsInvariantes() throws Exception {
        long p = produto("10.00", 0);
        semearLotes(p, "A:60:6");
        long lote = jdbc.queryForObject("select id from lote_estoque where produto_id = ?", Long.class, p);

        mvc.perform(post("/api/v1/produtos/" + p + "/ajustes-estoque").with(bearer(admin())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantidade\": -2, \"motivo\": \"Contagem com lotes desligados\", \"loteId\": " + lote + "}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.loteSaldoResultante").value(4));
        // Sem lote informado, o fluxo antigo de ajuste recusa produto controlado (nenhum caminho ignora o lote).
        mvc.perform(post("/api/v1/produtos/" + p + "/ajustes-estoque").with(bearer(admin())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantidade\": -1, \"motivo\": \"Sem o lote\"}")).andExpect(status().isBadRequest());
        assertThat(saldo(p)).isEqualTo(4);
    }

    @Test
    void moduloContratadoNaoAutorizaAcaoPrivilegiadaSemOPapelAdequado() throws Exception {
        long p = produto("10.00", 0);
        semearLotes(p, "A:60:6");
        long lote = jdbc.queryForObject("select id from lote_estoque where produto_id = ?", Long.class, p);
        String vendas = vendas();
        long venda = venderOk(vendas, vendaJson("PIX", "0.00", "null", p, 1));

        // Estoque e PDV estão contratados, mas ajuste e cancelamento são ADMIN: VENDAS e SERVICO recebem 403 do RBAC (não do módulo).
        for (String url : new String[] {"/api/v1/produtos/" + p + "/ajustes-estoque", "/api/v1/vendas/" + venda + "/cancelar"}) {
            mvc.perform(post(url).with(bearer(vendas)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"quantidade\": -1, \"motivo\": \"Ação privilegiada sem papel\", \"loteId\": " + lote + "}"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").doesNotExist());
            mvc.perform(post(url).with(comPapel("SERVICO")).contentType(MediaType.APPLICATION_JSON).content("{\"motivo\": \"Ação privilegiada sem papel\"}"))
                    .andExpect(status().isForbidden());
        }
        assertThat(saldo(p)).isEqualTo(5);
    }
}
