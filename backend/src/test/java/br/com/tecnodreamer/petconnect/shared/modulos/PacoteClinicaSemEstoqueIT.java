package br.com.tecnodreamer.petconnect.shared.modulos;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pacote Clínica exato do catálogo (sem estoque): a vacina é registrada na carteira sem baixar nada; a integração com lotes não existe. */
@TestPropertySource(properties = "petconnect.modulos=clientes,pets,clinica,agenda,caixa,financeiro,relatorios")
class PacoteClinicaSemEstoqueIT extends PacoteIntegrationTest {

    @Test
    void osModulosDaInstalacaoSaoOsDoPacoteDoCatalogo() throws Exception {
        confereQueAInstalacaoTemOsModulosDoPacote("clinica");
        consultar("/api/v1/modulos", admin()).andExpect(status().isOk()).andExpect(jsonPath("$.integracoes.length()").value(0));
    }

    @Test
    void vacinacaoConcluidaRegistraACarteiraELancaAReceitaSemEstoque() throws Exception {
        long vacina = vacinaId("Cão", "Raiva (Antirrábica Canina)");
        long pet = novoPetDeTutorNovo("Cão");
        long agendamento = agendar(pet, servicoVacinacao(), 6);
        emAndamento(agendamento);
        long baixasAntes = jdbc.queryForObject("select count(*) from movimentacao_estoque where tipo = 'CONSUMO_CLINICO'", Long.class);

        mudarStatus(agendamento, "SERVICO", """
                {"status": "CONCLUIDO", "valorFinal": 80.0,
                 "vacina": {"vacinaId": %d, "dataAplicacao": "%s", "dataProxima": "%s", "veterinario": "Dra. Ana", "lote": "LOTE-ANOTADO-NA-MAO"}}"""
                .formatted(vacina, hoje(), hoje().plusYears(1))).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select lote from carteira_vacina where pet_id = ?", String.class, pet)).isEqualTo("LOTE-ANOTADO-NA-MAO");
        assertThat(jdbc.queryForObject("select count(*) from movimentacao_estoque where tipo = 'CONSUMO_CLINICO'", Long.class))
                .as("sem estoque contratado, nenhuma baixa").isEqualTo(baixasAntes);
        consultar("/api/v1/pets/" + pet + "/vacinas", admin()).andExpect(status().isOk());
        exigirTodosIndisponiveis(admin(), "/api/v1/produtos", "/api/v1/vacinas/estoque", "/api/v1/lotes", "/api/v1/vendas");
    }
}
