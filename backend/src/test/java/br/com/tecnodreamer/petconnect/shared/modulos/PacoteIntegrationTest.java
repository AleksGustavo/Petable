package br.com.tecnodreamer.petconnect.shared.modulos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Apoio da homologação por pacote (etapa 09): cada IT sobe a aplicação com os módulos EXATOS do pacote do catálogo da etapa 06 e
 * exercita o fluxo comercial pela API, como um cliente real faria, sem acesso direto ao banco para o que o pacote expõe.
 */
abstract class PacoteIntegrationTest extends ModulosIntegrationTest {

    protected static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Autowired
    ModulosAtivos modulosAtivos;

    /** Módulos do pacote no catálogo (a fonte da oferta comercial). */
    protected static Set<String> modulosDoPacote(String pacote) throws Exception {
        try (InputStream in = PacoteIntegrationTest.class.getResourceAsStream("/modulos/catalogo.json")) {
            JsonNode raiz = new ObjectMapper().readTree(in);
            for (JsonNode p : raiz.get("pacotes")) {
                if (p.get("id").asText().equals(pacote)) {
                    Set<String> ids = new HashSet<>();
                    p.get("modulos").forEach(m -> ids.add(m.asText()));
                    return ids;
                }
            }
        }
        throw new IllegalArgumentException("pacote inexistente no catálogo: " + pacote);
    }

    protected void confereQueAInstalacaoTemOsModulosDoPacote(String pacote, String... extras) throws Exception {
        Set<String> esperado = modulosDoPacote(pacote);
        esperado.addAll(Set.of(extras));
        assertThat(modulosAtivos.ativos()).as("módulos ativos × pacote '" + pacote + "' do catálogo").containsExactlyInAnyOrderElementsOf(esperado);
    }

    protected ResultActions postar(String url, String token, String json) throws Exception {
        return mvc.perform(post(url).with(bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected int codigo(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getStatus();
    }

    protected void exigirTodosIndisponiveis(String token, String... urls) throws Exception {
        for (String url : urls) {
            exigirIndisponivel(consultar(url, token));
        }
    }

    protected String relatorio(String tipo, String token) throws Exception {
        return mvc.perform(get("/api/v1/relatorios/" + tipo).param("dataInicio", hoje().toString()).param("dataFim", hoje().toString())
                .with(bearer(token))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    /** I1/I2/I3/I5/I7 do sistema inteiro: o mesmo que o monitoramento usa. */
    protected void exigirEstoqueConsistente() throws Exception {
        String corpo = consultar("/api/v1/estoque/reconciliacao", admin()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat((Boolean) JsonPath.read(corpo, "$.consistente")).as("reconciliação do estoque: " + corpo).isTrue();
    }

    // ------------------------------------------------------------------ agenda (usada por pet shop, clínica e completo)

    private final Map<String, String> operadores = new HashMap<>();

    protected long novoPetDeTutorNovo(String especie) throws Exception {
        return criarPet(criarCliente(unico("Tutor Pacote")), unico("Pet"), especie);
    }

    protected long servicoPorNome(String nome) {
        return jdbc.queryForObject("select id from servico where nome = ?", Long.class, nome);
    }

    protected long servicoVacinacao() {
        jdbc.update("insert ignore into servico (nome, preco, duracao_minutos) values ('Vacinação', 80.00, 15)");
        return servicoPorNome("Vacinação");
    }

    protected long agendar(long petId, long servicoId, int diasAFrente) throws Exception {
        LocalDateTime quando = LocalDate.now(FUSO).plusDays(diasAFrente).atTime(10, 0);
        String corpo = mvc.perform(post("/api/v1/agendamentos").with(comPapel("VENDAS")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"petId\": %d, \"servicoId\": %d, \"funcionarioId\": null, \"dataHora\": \"%s\", \"observacoes\": \"pacote\"}"
                                .formatted(petId, servicoId, quando)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(corpo, "$.id")).longValue();
    }

    /** Usuário real do papel com caixa aberto: concluir atendimento lança a receita no caixa de quem conclui. */
    protected String operadorComCaixa(String papel) throws Exception {
        String token = operadores.get(papel);
        if (token == null) {
            String username = nomeUsuario();
            criarUsuario(username, papel);
            token = login(username, SENHA);
            abrirCaixa(token);
            operadores.put(papel, token);
        }
        return token;
    }

    protected ResultActions mudarStatus(long id, String papel, String json) throws Exception {
        boolean conclusao = json.contains("\"CONCLUIDO\"");
        var req = post("/api/v1/agendamentos/" + id + "/status").contentType(MediaType.APPLICATION_JSON);
        if (conclusao) {
            String corpo = json.contains("formaPagamento") ? json : json.replaceFirst("\\{","{\"formaPagamento\": \"PIX\", ");
            return mvc.perform(req.with(bearer(operadorComCaixa(papel))).content(corpo));
        }
        return mvc.perform(req.with(comPapel(papel)).content(json));
    }

    protected void emAndamento(long id) throws Exception {
        mudarStatus(id, "VENDAS", "{\"status\": \"CONFIRMADO\"}").andExpect(status().isOk());
        mudarStatus(id, "SERVICO", "{\"status\": \"EM_ANDAMENTO\"}").andExpect(status().isOk());
    }

    protected void desligarVinculosDeVacina() {
        jdbc.update("update vacina set produto_id = null");
    }
}
