package br.com.tecnodreamer.petconnect.shared.modulos;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Homologação do pacote Clínica (clientes, pets, clinica, agenda, caixa, financeiro, relatórios) COM a vacina baixando estoque, que o
 * catálogo manda acrescentar (produtos, estoque, lotes): atendimento de vacinação concluído, dose consumida do lote que vence primeiro
 * e rastreabilidade do lote até o animal. O pacote é PARCIAL (sem prontuário completo, prescrição, exames); só se vende como agenda e
 * carteira de vacinação.
 */
@TestPropertySource(properties = "petconnect.modulos=clientes,pets,clinica,agenda,caixa,financeiro,relatorios,produtos,estoque,lotes")
class PacoteClinicaIT extends PacoteIntegrationTest {

    @AfterEach
    void desligarVinculos() {
        desligarVinculosDeVacina();
    }

    @Test
    void osModulosDaInstalacaoSaoOsDoPacoteMaisOEstoqueDaVacina() throws Exception {
        confereQueAInstalacaoTemOsModulosDoPacote("clinica", "produtos", "estoque", "lotes");
        consultar("/api/v1/modulos", admin()).andExpect(status().isOk()).andExpect(jsonPath("$.integracoes[0]").value("vacina-estoque"));
    }

    private long produtoDaVacina(long vacinaId, String... lotes) throws Exception {
        long produto = produtoControlado("80.00", lotes);
        mvc.perform(put("/api/v1/vacinas/" + vacinaId + "/produto").with(bearer(admin())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"produtoId\": " + produto + "}")).andExpect(status().isOk());
        return produto;
    }

    private String conclusaoVacinacao(long vacinaId) {
        LocalDate hoje = LocalDate.now(FUSO);
        return """
                {"status": "CONCLUIDO", "valorFinal": 80.0,
                 "vacina": {"vacinaId": %d, "dataAplicacao": "%s", "dataProxima": "%s", "veterinario": "Dra. Ana", "lote": null}}"""
                .formatted(vacinaId, hoje, hoje.plusYears(1));
    }

    @Test
    void atendimentoDeVacinacaoConsomeADoseDoLoteQueVencePrimeiroERastreiaAteOAnimal() throws Exception {
        long vacina = vacinaId("Cão", "Raiva (Antirrábica Canina)");
        long produto = produtoDaVacina(vacina, "VAC-TARDE:200:3", "VAC-CEDO:40:2");
        long pet = novoPetDeTutorNovo("Cão");
        long agendamento = agendar(pet, servicoVacinacao(), 4);
        emAndamento(agendamento);

        mudarStatus(agendamento, "SERVICO", conclusaoVacinacao(vacina)).andExpect(status().isOk());

        assertThat(saldoLote(produto, "VAC-CEDO")).as("FEFO: sai do que vence antes").isEqualTo(1);
        assertThat(saldoLote(produto, "VAC-TARDE")).isEqualTo(3);
        assertThat(movimentosDoLote(produto, "VAC-CEDO", "CONSUMO_CLINICO")).isEqualTo(1);
        // Rastreabilidade: a carteira do animal guarda o lote físico e o rastreio do lote mostra o consumo clínico.
        assertThat(jdbc.queryForObject("select lote from carteira_vacina where pet_id = ?", String.class, pet)).isEqualTo("VAC-CEDO");
        String rastreio = consultar("/api/v1/lotes/" + loteId(produto, "VAC-CEDO") + "/rastreio", admin()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat((Integer) JsonPath.read(rastreio, "$.movimentos.length()")).isGreaterThanOrEqualTo(2);
        assertThat(rastreio).contains("CONSUMO_CLINICO");
        consultar("/api/v1/pets/" + pet + "/vacinas", admin()).andExpect(status().isOk()).andExpect(jsonPath("$[0].lote").value("VAC-CEDO"));
        exigirEstoqueConsistente();
    }

    @Test
    void semDoseVendavelOAtendimentoNaoConcluiENadaFicaGravado() throws Exception {
        long vacina = vacinaId("Cão", "V8 Polivalente");
        long produto = produtoDaVacina(vacina, "VAC-VENC:-1:2");
        long pet = novoPetDeTutorNovo("Cão");
        long agendamento = agendar(pet, servicoVacinacao(), 5);
        emAndamento(agendamento);

        mudarStatus(agendamento, "SERVICO", conclusaoVacinacao(vacina)).andExpect(status().isConflict());

        assertThat(saldoLote(produto, "VAC-VENC")).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from carteira_vacina where pet_id = ?", Integer.class, pet)).isZero();
        assertThat(jdbc.queryForObject("select status from agendamento where id = ?", String.class, agendamento)).isEqualTo("EM_ANDAMENTO");
    }

    @Test
    void venderNoBalcaoNaoFazParteDoPacoteClinica() throws Exception {
        exigirTodosIndisponiveis(admin(), "/api/v1/vendas", "/api/v1/compras", "/api/v1/fornecedores");
    }
}
