package br.com.tecnodreamer.petconnect.agendamento.web;

import com.jayway.jsonpath.JsonPath;
import br.com.tecnodreamer.petconnect.pet.web.PetIntegrationTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Apoio dos ITs de agendamento: mesmo contexto Spring dos demais ITs. */
abstract class AgendaIntegrationTest extends PetIntegrationTest {

    protected static final String URL = "/api/v1/agendamentos";
    protected static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    /** Um dia útil no futuro, longe o bastante para não virar passado durante o teste. */
    protected static LocalDate dia(int diasAFrente) {
        return LocalDate.now(FUSO).plusDays(diasAFrente);
    }

    protected static LocalDateTime em(int diasAFrente, int hora, int minuto) {
        return dia(diasAFrente).atTime(hora, minuto);
    }

    protected long servicoId(String nome) {
        return jdbc.queryForObject("select id from servico where nome = ?", Long.class, nome);
    }

    /** O serviço "Vacinação" não vem na V2: cria uma vez (como o ADMIN faria) e reaproveita. */
    protected long servicoVacinacaoId() {
        jdbc.update("insert ignore into servico (nome, preco, duracao_minutos) values ('Vacinação', 80.00, 15)");
        return servicoId("Vacinação");
    }

    protected static String agendamentoJson(long petId, long servicoId, LocalDateTime dataHora) {
        return """
                {"petId": %d, "servicoId": %d, "funcionarioId": null, "dataHora": "%s", "observacoes": "IT"}"""
                .formatted(petId, servicoId, dataHora);
    }

    protected ResultActions postarAgendamento(long petId, long servicoId, LocalDateTime dataHora) throws Exception {
        return mvc.perform(post(URL).with(comPapel("VENDAS")).contentType(MediaType.APPLICATION_JSON)
                .content(agendamentoJson(petId, servicoId, dataHora)));
    }

    protected long criarAgendamento(long petId, long servicoId, LocalDateTime dataHora) throws Exception {
        String body = postarAgendamento(petId, servicoId, dataHora).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private final java.util.Map<String, String> tokensComCaixa = new java.util.HashMap<>();

    /**
     * Etapa 06: a conclusão exige usuário real (uid), caixa aberto e forma de pagamento.
     * Para as demais transições basta o papel no JWT. Sem "formaPagamento" no JSON de
     * conclusão, envia PIX (os testes antigos seguem verificando as mesmas regras).
     */
    protected ResultActions mudarStatus(long id, String papel, String json) throws Exception {
        if (!json.contains("\"CONCLUIDO\"")) {
            return mvc.perform(post(URL + "/" + id + "/status").with(comPapel(papel))
                    .contentType(MediaType.APPLICATION_JSON).content(json));
        }
        String corpo = json.contains("formaPagamento") ? json
                : json.replaceFirst("\\{", "{\"formaPagamento\": \"PIX\", ");
        return mvc.perform(post(URL + "/" + id + "/status").with(bearer(operadorComCaixa(papel)))
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    /** Usuário real do papel, com caixa aberto (um por papel em cada teste). */
    protected String operadorComCaixa(String papel) throws Exception {
        String token = tokensComCaixa.get(papel);
        if (token == null) {
            String username = nomeUsuario();
            criarUsuario(username, papel);
            token = login(username, SENHA);
            abrirCaixa(token);
            tokensComCaixa.put(papel, token);
        }
        return token;
    }

    protected static String statusJson(String status) {
        return "{\"status\": \"" + status + "\"}";
    }

    /** Leva o agendamento para EM_ANDAMENTO pelas transições reais. */
    protected void emAndamento(long id) throws Exception {
        mudarStatus(id, "VENDAS", statusJson("CONFIRMADO")).andExpect(status().isOk());
        mudarStatus(id, "SERVICO", statusJson("EM_ANDAMENTO")).andExpect(status().isOk());
    }

    protected long novoPet(String especie) throws Exception {
        return criarPet(criarCliente(unico("Tutor Agenda")), unico("Pet"), especie);
    }
}
