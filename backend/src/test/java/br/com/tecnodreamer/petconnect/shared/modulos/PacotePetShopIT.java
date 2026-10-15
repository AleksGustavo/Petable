package br.com.tecnodreamer.petconnect.shared.modulos;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Homologação do pacote Pet Shop (clientes, produtos, estoque, lotes, caixa, pdv, compras, financeiro, pets, agenda, relatórios):
 * venda + serviço + entrada de mercadoria no mesmo dia, permissões por papel, e a parte clínica (carteira e consultas) fora da oferta.
 */
@TestPropertySource(properties = "petconnect.modulos=clientes,produtos,estoque,lotes,caixa,pdv,compras,financeiro,pets,agenda,relatorios")
class PacotePetShopIT extends PacoteIntegrationTest {

    @Test
    void osModulosDaInstalacaoSaoOsDoPacoteDoCatalogo() throws Exception {
        confereQueAInstalacaoTemOsModulosDoPacote("petshop");
    }

    @Test
    void vendaServicoEEntradaNoMesmoDiaFecham() throws Exception {
        // Entrada: compra recebida soma saldo, grava a movimentação e abre a conta a pagar.
        long p = produto("30.00", 0);
        long fornecedor = criarFornecedor(unico("Fornecedor PetShop"));
        long compra = criarCompra(fornecedor, String.valueOf(p), "6:12.00");
        receber(compra, hoje()).andExpect(status().isOk());
        assertThat(saldo(p)).isEqualTo(6);
        assertThat(jdbc.queryForObject("select count(*) from conta_pagar where compra_id = ?", Integer.class, compra)).isEqualTo(1);

        // Venda no balcão.
        String operador = vendas();
        long venda = venderOk(operador, vendaJson("PIX", "0.00", "null", p, 2));
        assertThat(saldo(p)).isEqualTo(4);

        // Serviço: banho agendado, atendido e concluído; a receita entra no caixa de quem conclui.
        long pet = novoPetDeTutorNovo("Cão");
        long agendamento = agendar(pet, servicoPorNome("Banho"), 3);
        emAndamento(agendamento);
        mudarStatus(agendamento, "SERVICO", "{\"status\": \"CONCLUIDO\", \"formaPagamento\": \"DINHEIRO\", \"valorFinal\": 55.00}").andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select count(*) from movimentacao_financeira where categoria = 'SERVICO' and caixa_id is not null and valor = 55.00",
                Integer.class)).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from movimentacao_financeira where chave_origem = ?", Integer.class, "VENDA:" + venda)).isEqualTo(1);
        consultar("/api/v1/financeiro/lancamentos", admin()).andExpect(status().isOk());
        exigirEstoqueConsistente();
    }

    @Test
    void permissoesPorPapelContinuamValendoComTodosOsModulosDoPacote() throws Exception {
        long p = produto("10.00", 2);
        long fornecedor = criarFornecedor(unico("Fornecedor Papel"));
        long compra = criarCompra(fornecedor, String.valueOf(p), "1:5.00");
        String vendas = vendas();

        // VENDAS não recebe compra nem mexe em financeiro gerencial; SERVICO não vê vendas nem compras.
        assertThat(codigo(postar("/api/v1/compras/" + compra + "/receber", vendas,
                "{\"dataEntrega\": \"" + hoje() + "\", \"dataVencimento\": \"" + hoje().plusDays(30) + "\"}"))).isEqualTo(403);
        consultar("/api/v1/financeiro/lancamentos", vendas).andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").doesNotExist());
        mvc.perform(get("/api/v1/vendas").with(comPapel("SERVICO"))).andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").doesNotExist());
        mvc.perform(get("/api/v1/compras").with(comPapel("SERVICO"))).andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").doesNotExist());
        assertThat(saldo(p)).isEqualTo(2);
    }

    @Test
    void aParteClinicaNaoFazParteDaOfertaPetShop() throws Exception {
        long pet = novoPetDeTutorNovo("Cão");
        for (String token : new String[] {admin(), vendas()}) {
            exigirTodosIndisponiveis(token, "/api/v1/pets/" + pet + "/vacinas", "/api/v1/pets/" + pet + "/consultas", "/api/v1/vacinas/estoque");
        }
        // O cadastro do pet e a agenda continuam normais.
        consultar("/api/v1/pets/" + pet, admin()).andExpect(status().isOk());
        consultar("/api/v1/agendamentos?inicio=" + hoje() + "&fim=" + hoje().plusDays(3), admin()).andExpect(status().isOk());
    }

    @Test
    void lotesDeRacaoEMedicamentoOperamNoPacote() throws Exception {
        long p = produtoControlado("40.00", "RACAO-A:45:3", "RACAO-B:120:3");
        venderOk(vendas(), vendaJson("PIX", "0.00", "null", p, 4));
        assertThat(saldoLote(p, "RACAO-A")).isZero();
        assertThat(saldoLote(p, "RACAO-B")).isEqualTo(2);
        consultar("/api/v1/estoque/alertas", admin()).andExpect(status().isOk());
        exigirEstoqueConsistente();
    }
}
