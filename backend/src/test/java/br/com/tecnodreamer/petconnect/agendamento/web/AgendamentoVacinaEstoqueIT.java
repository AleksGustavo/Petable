package br.com.tecnodreamer.petconnect.agendamento.web;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Concluir o agendamento de vacinação aplica a dose pela mesma regra da carteira: com a vacina ligada a um produto com lote, a
 * conclusão baixa uma dose (FEFO) e, se qualquer parte falhar, nada fica gravado (dose, serviço realizado, financeiro e estoque).
 */
class AgendamentoVacinaEstoqueIT extends AgendaIntegrationTest {

    @AfterEach
    void desligarVinculos() {
        jdbc.update("update vacina set produto_id = null");
    }

    private String conclusao(long vacinaId, String lote) {
        LocalDate hoje = LocalDate.now(FUSO);
        return """
                {"status": "CONCLUIDO", "valorFinal": 80.0,
                 "vacina": {"vacinaId": %d, "dataAplicacao": "%s", "dataProxima": "%s", "veterinario": "Dra. Ana", "lote": %s}}"""
                .formatted(vacinaId, hoje, hoje.plusYears(1), lote == null ? "null" : "\"" + lote + "\"");
    }

    /** Produto com controle de lote e as doses informadas, ligado à vacina. Devolve o id do produto. */
    private long produtoDaVacina(long vacinaId, int dias, int doses) throws Exception {
        String admin = login(ADMIN_USER, ADMIN_PASSWORD);
        String corpo = mvc.perform(post("/api/v1/produtos").with(comPapel("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "%s", "descricao": "IT", "categoriaId": %d, "codigoBarras": null, "precoCusto": 50.00,
                                 "precoVenda": 80.00, "estoqueMinimo": 1}""".formatted(unico("Vacina estoque"),
                                jdbc.queryForObject("select id from categoria_produto limit 1", Long.class))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long produto = ((Number) JsonPath.read(corpo, "$.id")).longValue();
        mvc.perform(post("/api/v1/produtos/" + produto + "/ajustes-estoque").with(bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantidade\": " + doses + ", \"motivo\": \"Saldo inicial do teste\"}")).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/produtos/" + produto + "/controle-lote").with(bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"lotes\": [{\"codigoLote\": \"VAC-" + produto + "\", \"validade\": \"" + LocalDate.now(FUSO).plusDays(dias)
                        + "\", \"quantidade\": " + doses + "}]}")).andExpect(status().isCreated());
        mvc.perform(put("/api/v1/vacinas/" + vacinaId + "/produto").with(bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"produtoId\": " + produto + "}")).andExpect(status().isOk());
        return produto;
    }

    private int saldoDoLote(long produto) {
        return jdbc.queryForObject("select saldo from lote_estoque where produto_id = ?", Integer.class, produto);
    }

    @Test
    void concluirVacinacaoBaixaADoseDoLoteEGravaOLoteNaCarteira() throws Exception {
        long vacina = vacinaId("Cão", "Raiva (Antirrábica Canina)");
        long produto = produtoDaVacina(vacina, 90, 3);
        long petId = novoPet("Cão");
        long id = criarAgendamento(petId, servicoVacinacaoId(), em(4, 9, 0));
        emAndamento(id);

        mudarStatus(id, "SERVICO", conclusao(vacina, null)).andExpect(status().isOk());

        assertThat(saldoDoLote(produto)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select quantidade_estoque from produto where id = ?", Integer.class, produto)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select lote from carteira_vacina where pet_id = ?", String.class, petId)).isEqualTo("VAC-" + produto);
        assertThat(jdbc.queryForObject("select count(*) from movimentacao_estoque where produto_id = ? and tipo = 'CONSUMO_CLINICO'",
                Integer.class, produto)).isEqualTo(1);
    }

    @Test
    void semLoteDisponivelAConclusaoFalhaENadaFicaGravado() throws Exception {
        long vacina = vacinaId("Cão", "V8 Polivalente");
        long produto = produtoDaVacina(vacina, 90, 1);
        // O único lote vence (simulado): não há dose vendável.
        jdbc.update("update lote_estoque set validade = ? where produto_id = ?", LocalDate.now(FUSO).minusDays(1), produto);
        long petId = novoPet("Cão");
        long id = criarAgendamento(petId, servicoVacinacaoId(), em(5, 9, 0));
        emAndamento(id);

        mudarStatus(id, "SERVICO", conclusao(vacina, null)).andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("select status from agendamento where id = ?", String.class, id)).isEqualTo("EM_ANDAMENTO");
        assertThat(jdbc.queryForObject("select count(*) from carteira_vacina where pet_id = ?", Integer.class, petId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from servico_realizado where agendamento_id = ?", Integer.class, id)).isZero();
        assertThat(saldoDoLote(produto)).isEqualTo(1);
    }

    @Test
    void loteDigitadoNaConclusaoQuandoOEstoqueEscolheERecusado() throws Exception {
        long vacina = vacinaId("Cão", "V10 Polivalente");
        long produto = produtoDaVacina(vacina, 90, 2);
        long petId = novoPet("Cão");
        long id = criarAgendamento(petId, servicoVacinacaoId(), em(6, 9, 0));
        emAndamento(id);

        mudarStatus(id, "SERVICO", conclusao(vacina, "L-MAO")).andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("select status from agendamento where id = ?", String.class, id)).isEqualTo("EM_ANDAMENTO");
        assertThat(saldoDoLote(produto)).isEqualTo(2);
    }
}
