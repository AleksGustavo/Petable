package br.com.tecnodreamer.petconnect.agendamento.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Etapa 06: serviço concluído vira receita no caixa de quem conclui (DA-08). */
class FinanceiroServicoIT extends AgendaIntegrationTest {

    private long emAndamentoNovo() throws Exception {
        long id = criarAgendamento(novoPet("Cão"), servicoId("Banho"), em(6, 10, 0));
        emAndamento(id);
        return id;
    }

    private String semCaixa(String papel) throws Exception {
        String username = nomeUsuario();
        criarUsuario(username, papel);
        return login(username, SENHA);
    }

    private Long realizado(long agendamentoId) {
        return jdbc.query("select id from servico_realizado where agendamento_id = ?", (rs, i) -> rs.getLong(1), agendamentoId)
                .stream().findFirst().orElse(null);
    }

    @Test
    void servicoConcluidoDeveGerarReceita() throws Exception {
        long id = emAndamentoNovo();

        mudarStatus(id, "SERVICO", "{\"status\": \"CONCLUIDO\", \"formaPagamento\": \"DINHEIRO\", \"valorFinal\": 48.50}")
                .andExpect(status().isOk());

        Long sr = realizado(id);
        var l = jdbc.queryForMap("select tipo, categoria, valor, forma_pagamento, caixa_id from movimentacao_financeira "
                + "where servico_realizado_id = ?", sr);
        assertThat(l).containsEntry("tipo", "ENTRADA").containsEntry("categoria", "SERVICO").containsEntry("forma_pagamento", "DINHEIRO");
        assertThat(new BigDecimal(l.get("valor").toString())).isEqualByComparingTo("48.50");
        assertThat(l.get("caixa_id")).isNotNull();
    }

    @Test
    void servicoSemCaixaDeveRetornar409() throws Exception {
        long id = emAndamentoNovo();

        mvc.perform(post(URL + "/" + id + "/status").with(bearer(semCaixa("SERVICO"))).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"CONCLUIDO\", \"formaPagamento\": \"PIX\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Não há caixa aberto para o operador atual. Abra o caixa antes de continuar."));

        assertThat(realizado(id)).isNull();
        assertThat(jdbc.queryForObject("select status from agendamento where id = ?", String.class, id)).isEqualTo("EM_ANDAMENTO");
    }

    @Test
    void servicoSemFormaDeveRetornar400() throws Exception {
        long id = emAndamentoNovo();

        mvc.perform(post(URL + "/" + id + "/status").with(bearer(operadorComCaixa("SERVICO"))).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"CONCLUIDO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.formaPagamento").exists());
        assertThat(realizado(id)).isNull();
    }

    /**
     * Força falha na gravação do lançamento: reserva as próximas chaves SERVICO:n com
     * lançamentos-sentinela (a UNIQUE de chave_origem barra o do serviço). A conclusão
     * inteira tem de voltar atrás.
     */
    @Test
    void falhaFinanceiraDeveFazerRollback() throws Exception {
        long id = emAndamentoNovo();
        long base = jdbc.queryForObject("select coalesce(max(id), 0) from servico_realizado", Long.class);
        try {
            for (long n = base + 1; n <= base + 200; n++) {
                jdbc.update("insert into movimentacao_financeira (tipo, categoria, descricao, valor, data_movimentacao, chave_origem) "
                        + "values ('ENTRADA', 'OUTROS', 'sentinela IT', 1.00, current_date, ?)", "SERVICO:" + n);
            }

            mudarStatus(id, "SERVICO", "{\"status\": \"CONCLUIDO\", \"formaPagamento\": \"PIX\"}")
                    .andExpect(status().isConflict());

            assertThat(realizado(id)).isNull();
            assertThat(jdbc.queryForObject("select status from agendamento where id = ?", String.class, id)).isEqualTo("EM_ANDAMENTO");
        } finally {
            jdbc.update("delete from movimentacao_financeira where descricao = 'sentinela IT'");
        }
    }
}
