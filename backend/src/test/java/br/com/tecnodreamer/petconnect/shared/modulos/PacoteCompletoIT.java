package br.com.tecnodreamer.petconnect.shared.modulos;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Homologação do pacote Completo (os 12 módulos): fluxo integrado ponta a ponta (compra com lote, venda FEFO, vacinação consumindo
 * lote, cancelamento, financeiro e relatórios) terminando com a reconciliação do estoque consistente; mais a repetição de operações
 * (mesma chave de idempotência) sem duplicar venda, baixa nem lançamento.
 */
@TestPropertySource(properties = "petconnect.modulos=clientes,produtos,estoque,lotes,caixa,pdv,compras,financeiro,pets,clinica,agenda,relatorios")
class PacoteCompletoIT extends PacoteIntegrationTest {

    @AfterEach
    void desligarVinculos() {
        desligarVinculosDeVacina();
    }

    @Test
    void osModulosDaInstalacaoSaoOsDoPacoteDoCatalogo() throws Exception {
        confereQueAInstalacaoTemOsModulosDoPacote("completo");
        consultar("/api/v1/modulos", admin()).andExpect(status().isOk())
                .andExpect(jsonPath("$.modulos.length()").value(12))
                .andExpect(jsonPath("$.relatorios.length()").value(9))
                .andExpect(jsonPath("$.integracoes[0]").value("vacina-estoque"));
    }

    @Test
    void fluxoIntegradoPontaAPonta() throws Exception {
        // 1. Compra recebida com lote (entrada + conta a pagar).
        long p = produtoControlado("60.00", "BASE:300:2");
        long fornecedor = criarFornecedor(unico("Fornecedor Completo"));
        long compra = criarCompra(fornecedor, String.valueOf(p), "8:20.00");
        receberComLotes(compra, p, "NOVO:30:8").andExpect(status().isOk());
        assertThat(saldo(p)).isEqualTo(10);
        assertThat(jdbc.queryForObject("select count(*) from conta_pagar where compra_id = ?", Integer.class, compra)).isEqualTo(1);

        // 2. Venda FEFO no balcão, com cliente vinculado.
        long cliente = criarCliente(unico("Cliente Completo"));
        String operador = vendas();
        long venda = venderOk(operador, vendaJson("PIX", "0.00", "null", p, 5));
        assertThat(saldoLote(p, "NOVO")).isEqualTo(3);
        assertThat(saldoLote(p, "BASE")).isEqualTo(2);

        // 3. Vacinação com a dose vindo de um produto de lote.
        long vacina = vacinaId("Cão", "Raiva (Antirrábica Canina)");
        long produtoVacina = produtoControlado("80.00", "VAC-1:120:4");
        mvc.perform(put("/api/v1/vacinas/" + vacina + "/produto").with(bearer(admin())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"produtoId\": " + produtoVacina + "}")).andExpect(status().isOk());
        long pet = criarPet(cliente, unico("Pet Completo"), "Cão");
        long agendamento = agendar(pet, servicoVacinacao(), 7);
        emAndamento(agendamento);
        LocalDate hoje = LocalDate.now(FUSO);
        mudarStatus(agendamento, "SERVICO", """
                {"status": "CONCLUIDO", "valorFinal": 80.0,
                 "vacina": {"vacinaId": %d, "dataAplicacao": "%s", "dataProxima": "%s", "veterinario": "Dra. Ana", "lote": null}}"""
                .formatted(vacina, hoje, hoje.plusYears(1))).andExpect(status().isOk());
        assertThat(saldoLote(produtoVacina, "VAC-1")).isEqualTo(3);

        // 4. Cancelamento da venda devolvendo ao lote original.
        cancelarVenda(admin(), venda, "RETORNAR").andExpect(status().isOk());
        assertThat(saldoLote(p, "NOVO") + saldoLote(p, "BASE")).isEqualTo(10);

        // 5. Financeiro e relatórios (todos os nove, somente leitura, ADMIN).
        consultar("/api/v1/financeiro/lancamentos", admin()).andExpect(status().isOk());
        consultar("/api/v1/financeiro/contas-a-pagar", admin()).andExpect(status().isOk());
        for (String tipo : new String[] {"vendas", "produtos-mais-vendidos", "curva-abc", "estoque", "margem", "servicos", "despesas", "caixas", "contas"}) {
            assertThat(relatorio(tipo, admin())).as("relatório " + tipo).isNotBlank();
        }
        mvc.perform(get("/api/v1/relatorios/vendas/csv").param("dataInicio", hoje.toString()).param("dataFim", hoje.toString()).with(bearer(admin())))
                .andExpect(status().isOk());

        // 6. Nenhuma invariante de estoque quebrou no caminho.
        exigirEstoqueConsistente();
    }

    @Test
    void repetirAMesmaVendaNaoDuplicaNada() throws Exception {
        long p = produto("10.00", 5);
        String operador = vendas();
        String chave = chave();
        String json = vendaJson("PIX", "0.00", "null", p, 2);

        vender(operador, chave, json).andExpect(status().isCreated());
        // A repetição (cliente que não recebeu a resposta, retentativa de rede) devolve a mesma venda, sem efeito novo.
        int repetida = codigo(vender(operador, chave, json));
        assertThat(repetida).isIn(200, 201);

        assertThat(saldo(p)).as("baixa uma vez só").isEqualTo(3);
        assertThat(movimentacoes(p, "SAIDA_VENDA")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from venda v join item_venda i on i.venda_id = v.id where i.produto_id = ?", Integer.class, p))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from movimentacao_financeira f join venda v on v.id = f.venda_id join item_venda i on i.venda_id = v.id "
                + "where i.produto_id = ? and f.chave_origem like 'VENDA:%'", Integer.class, p)).isEqualTo(1);
        exigirEstoqueConsistente();
    }

    @Test
    void cancelarDuasVezesNaoDevolveEstoqueDuasVezes() throws Exception {
        long p = produto("10.00", 4);
        long venda = venderOk(vendas(), vendaJson("PIX", "0.00", "null", p, 2));

        cancelar(admin(), venda, "Primeiro cancelamento").andExpect(status().isOk());
        assertThat(codigo(cancelar(admin(), venda, "Segundo cancelamento"))).isEqualTo(409);

        assertThat(saldo(p)).isEqualTo(4);
        assertThat(movimentacoes(p, "ESTORNO_VENDA")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from movimentacao_financeira where chave_origem = ?", Integer.class, "VENDA_ESTORNO:" + venda)).isEqualTo(1);
        exigirEstoqueConsistente();
    }
}
