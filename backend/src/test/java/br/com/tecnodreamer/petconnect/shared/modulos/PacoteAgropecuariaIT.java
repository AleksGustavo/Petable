package br.com.tecnodreamer.petconnect.shared.modulos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Homologação TÉCNICA do pacote Agropecuária (clientes, produtos, estoque, lotes, caixa, pdv, compras, financeiro, relatórios):
 * recebimento com lote, validade, FEFO, alertas e devolução funcionam. O pacote continua NÃO PRONTO para venda: não há venda a granel
 * nem quantidade decimal (D24), venda a prazo ou nota fiscal. Um teste trava essa marcação no catálogo.
 */
@TestPropertySource(properties = "petconnect.modulos=clientes,produtos,estoque,lotes,caixa,pdv,compras,financeiro,relatorios")
class PacoteAgropecuariaIT extends PacoteIntegrationTest {

    @Test
    void osModulosDaInstalacaoSaoOsDoPacoteDoCatalogo() throws Exception {
        confereQueAInstalacaoTemOsModulosDoPacote("agropecuaria");
    }

    @Test
    void oCatalogoNaoMarcaAgropecuariaComoPronta() throws Exception {
        try (var in = getClass().getResourceAsStream("/modulos/catalogo.json")) {
            for (JsonNode p : new ObjectMapper().readTree(in).get("pacotes")) {
                if (p.get("id").asText().equals("agropecuaria")) {
                    assertThat(p.get("pronto").asBoolean()).as("agropecuária depende de granel/decimal (D24)").isFalse();
                }
            }
        }
    }

    @Test
    void recebimentoValidadeFefoAlertasEDevolucao() throws Exception {
        long p = produtoControlado("25.00", "BASE:200:2");
        long fornecedor = criarFornecedor(unico("Fornecedor Agro"));
        long compra = criarCompra(fornecedor, String.valueOf(p), "10:9.00");

        // Recebimento: lote novo com validade curta entra junto do lote antigo.
        receberComLotes(compra, p, "SACO-NOVO:25:10").andExpect(status().isOk());
        assertThat(saldoLote(p, "SACO-NOVO")).isEqualTo(10);
        assertThat(saldo(p)).isEqualTo(12);

        // FEFO: vende o que vence antes (o recém-chegado, 25 dias) e não o lote de 200 dias.
        String operador = vendas();
        long venda = venderOk(operador, vendaJson("PIX", "0.00", "null", p, 4));
        assertThat(saldoLote(p, "SACO-NOVO")).isEqualTo(6);
        assertThat(saldoLote(p, "BASE")).isEqualTo(2);

        // Validade: vencido não vende (simula o vencimento do lote novo).
        jdbc.update("update lote_estoque set validade = ? where produto_id = ? and codigo_lote = 'SACO-NOVO'", hoje().minusDays(1), p);
        vender(operador, chave(), vendaJson("PIX", "0.00", "null", p, 3)).andExpect(status().isConflict());

        // Alertas: o vencido aparece, derivado do dado real.
        consultar("/api/v1/estoque/alertas", admin()).andExpect(status().isOk()).andExpect(jsonPath("$.vencidos.lotes").value(1));

        // Devolução: cancelar a venda devolvendo ao lote original recompõe exatamente o que saiu.
        cancelarVenda(admin(), venda, "RETORNAR").andExpect(status().isOk());
        assertThat(saldoLote(p, "BASE") + saldoLote(p, "SACO-NOVO")).isEqualTo(12);
        exigirEstoqueConsistente();
    }

    @Test
    void devolucaoDescartadaViraPerdaERegistraNoLoteOriginal() throws Exception {
        long p = produtoControlado("15.00", "L1:60:5");
        long venda = venderOk(vendas(), vendaJson("PIX", "0.00", "null", p, 2));

        cancelarVenda(admin(), venda, "DESCARTAR").andExpect(status().isOk());

        assertThat(saldoLote(p, "L1")).as("descartado não volta ao saldo vendável").isEqualTo(3);
        assertThat(movimentosDoLote(p, "L1", "ESTORNO_VENDA")).isEqualTo(1);
        assertThat(movimentosDoLote(p, "L1", "PERDA")).isEqualTo(1);
        exigirEstoqueConsistente();
    }

    @Test
    void agendaPetsEClinicaNaoExistemNestePacote() throws Exception {
        exigirTodosIndisponiveis(admin(), "/api/v1/agendamentos", "/api/v1/pets", "/api/v1/servicos", "/api/v1/especies", "/api/v1/vacinas/estoque");
    }
}
