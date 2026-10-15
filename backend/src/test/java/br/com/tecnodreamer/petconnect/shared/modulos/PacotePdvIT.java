package br.com.tecnodreamer.petconnect.shared.modulos;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Homologação do pacote PDV (produtos, estoque, caixa, pdv, relatórios): balcão e caixa funcionam de ponta a ponta, a venda baixa
 * pelo FEFO mesmo sem a tela de lotes e impede vencido, o estorno recompõe, e toda gestão avançada (compras, financeiro, clientes,
 * pets, agenda, lotes) fica negada.
 */
@TestPropertySource(properties = "petconnect.modulos=produtos,estoque,caixa,pdv,relatorios")
class PacotePdvIT extends PacoteIntegrationTest {

    @Test
    void osModulosDaInstalacaoSaoOsDoPacoteDoCatalogo() throws Exception {
        confereQueAInstalacaoTemOsModulosDoPacote("pdv");
    }

    @Test
    void vendaCaixaEEstornoFuncionamDePontaAPonta() throws Exception {
        long p = produto("20.00", 5);
        String operador = vendas();

        long venda = venderOk(operador, vendaJson("DINHEIRO", "0.00", "50.00", p, 2));

        assertThat(saldo(p)).isEqualTo(3);
        assertThat(movimentacoes(p, "SAIDA_VENDA")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from movimentacao_financeira where chave_origem = ?", Integer.class, "VENDA:" + venda)).isEqualTo(1);
        consultar("/api/v1/caixas/resumo-atual", operador).andExpect(status().isOk()).andExpect(jsonPath("$.aberto").value(true));
        consultar("/api/v1/vendas/" + venda, operador).andExpect(status().isOk());

        // Estorno (cancelamento): recompõe o saldo e lança o estorno financeiro, sem apagar a venda.
        cancelar(admin(), venda, "Cliente desistiu da compra").andExpect(status().isOk());
        assertThat(saldo(p)).isEqualTo(5);
        assertThat(movimentacoes(p, "ESTORNO_VENDA")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from movimentacao_financeira where chave_origem = ?", Integer.class, "VENDA_ESTORNO:" + venda)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from venda where id = ?", String.class, venda)).isEqualTo("CANCELADA");
        exigirEstoqueConsistente();
    }

    @Test
    void semATelaDeLotesAVendaAindaBaixaPeloFefoEImpedeVencido() throws Exception {
        long p = produto("10.00", 0);
        semearLotes(p, "TARDE:90:4", "CEDO:20:2", "VENCIDO:-3:6");
        String operador = vendas();

        venderOk(operador, vendaJson("PIX", "0.00", "null", p, 4));

        assertThat(saldoLote(p, "CEDO")).as("lote que vence antes sai primeiro").isZero();
        assertThat(saldoLote(p, "TARDE")).isEqualTo(2);
        assertThat(saldoLote(p, "VENCIDO")).as("vencido nunca é vendido").isEqualTo(6);
        // Só restam 2 vendáveis (TARDE): pedir 3 é recusado, mesmo havendo 6 vencidos no físico.
        vender(operador, chave(), vendaJson("PIX", "0.00", "null", p, 3)).andExpect(status().isConflict());
        assertThat(saldoLote(p, "VENCIDO")).isEqualTo(6);
    }

    @Test
    void gestaoAvancadaFicaNegadaParaTodosOsPapeis() throws Exception {
        for (String token : new String[] {admin(), vendas()}) {
            exigirTodosIndisponiveis(token, "/api/v1/compras", "/api/v1/fornecedores", "/api/v1/financeiro/lancamentos", "/api/v1/financeiro/contas-a-pagar",
                    "/api/v1/clientes", "/api/v1/pets", "/api/v1/agendamentos", "/api/v1/servicos", "/api/v1/lotes", "/api/v1/estoque/alertas",
                    "/api/v1/relatorios/contas", "/api/v1/relatorios/despesas", "/api/v1/relatorios/servicos");
        }
    }

    @Test
    void usuarioSemPapelNaoGanhaAcaoPrivilegiadaMesmoComOModuloContratado() throws Exception {
        long p = produto("10.00", 3);
        String operador = vendas();
        long venda = venderOk(operador, vendaJson("PIX", "0.00", "null", p, 1));

        // Cancelar e ajustar saldo são ADMIN: com estoque e PDV contratados, VENDAS recebe o 403 do RBAC (sem código de módulo).
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/vendas/" + venda + "/cancelar").with(bearer(operador))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"motivo\": \"Sem permissão para isto\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").doesNotExist());
        assertThat(codigo(postar("/api/v1/produtos/" + p + "/ajustes-estoque", operador, "{\"quantidade\": 5, \"motivo\": \"Sem permissão\"}"))).isEqualTo(403);
        assertThat(saldo(p)).isEqualTo(2);
    }

    @Test
    void relatoriosDeOrigemContratadaFuncionamEOsOutrosNao() throws Exception {
        long p = produto("10.00", 2);
        venderOk(vendas(), vendaJson("PIX", "0.00", "null", p, 1));
        assertThat(relatorio("vendas", admin())).isNotBlank();
        assertThat(relatorio("estoque", admin())).isNotBlank();
        assertThat(relatorio("caixas", admin())).isNotBlank();
    }
}
