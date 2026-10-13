package br.com.tecnodreamer.petconnect.pet.web;

import com.jayway.jsonpath.JsonPath;
import br.com.tecnodreamer.petconnect.EmailsCapturados;
import br.com.tecnodreamer.petconnect.IntegrationTest;
import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaRepository;
import br.com.tecnodreamer.petconnect.shared.audit.EventoAuditoria;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Apoio comum dos ITs do módulo de pets (mesmo contexto Spring de {@link IntegrationTest}). */
public abstract class PetIntegrationTest extends IntegrationTest {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected AuditoriaRepository auditoria;

    protected static RequestPostProcessor comPapel(String papel) {
        return jwt().jwt(j -> j.subject("teste-" + papel.toLowerCase()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + papel));
    }

    protected long especieId(String nome) {
        return jdbc.queryForObject("select id from especie where nome = ?", Long.class, nome);
    }

    protected long racaId(String especie, String nome) {
        return jdbc.queryForObject("select r.id from raca r join especie e on e.id = r.especie_id "
                + "where e.nome = ? and r.nome = ?", Long.class, especie, nome);
    }

    protected long vacinaId(String especie, String nome) {
        return jdbc.queryForObject("select v.id from vacina v join especie e on e.id = v.especie_id "
                + "where e.nome = ? and v.nome = ?", Long.class, especie, nome);
    }

    protected long criarCliente(String nome) throws Exception {
        String telefone = "119" + ThreadLocalRandom.current().nextLong(10_000_000L, 99_999_999L);
        String body = mvc.perform(post("/api/v1/clientes").with(comPapel("VENDAS"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "%s", "telefone": "%s"}""".formatted(nome, telefone)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    protected static String petJson(long clienteId, String nome, long especieId, Long racaId) {
        return """
                {"clienteId": %d, "nome": "%s", "especieId": %d, "racaId": %s,
                 "dataNascimento": "2023-05-20", "peso": 32.5, "cor": "Caramelo",
                 "porte": "GRANDE", "castrado": true}""".formatted(clienteId, nome, especieId, racaId);
    }

    protected long criarPet(long clienteId, String nome, String especie) throws Exception {
        String body = mvc.perform(post("/api/v1/pets").with(comPapel("VENDAS"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(petJson(clienteId, nome, especieId(especie), null)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    protected List<String> acoesAuditadas(long petId) {
        return acoesAuditadas("pet", petId);
    }

    protected List<String> acoesAuditadas(String entidade, long id) {
        return auditoria.findByEntidadeAndEntidadeId(entidade, String.valueOf(id), Pageable.unpaged())
                .map(EventoAuditoria::acao).toList();
    }

    protected static String unico(String prefixo) {
        return prefixo + " " + Long.toString(System.nanoTime(), 36);
    }

    // ------------------------------------------------------------ usuários (etapa 03)

    protected static final String SENHA = "Senha@Valida2026";

    @Autowired
    protected EmailsCapturados emails;

    protected static String nomeUsuario() {
        return "u" + Long.toString(System.nanoTime(), 36);
    }

    /** Cria usuário pela API como ADMIN e devolve o id. E-mail = username@petconnect.test. */
    protected long criarUsuario(String username, String papel) throws Exception {
        String body = mvc.perform(post("/api/v1/usuarios").with(comPapel("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "email": "%s@petconnect.test", "senha": "%s", "papel": "%s"}"""
                                .formatted(username, username, SENHA, papel)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    protected ResultActions tentarLogin(String login, String senha) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"login": "%s", "senha": "%s"}""".formatted(login, senha)));
    }

    /** Login real: devolve o JWT emitido pela API (passa pelo conversor que consulta o banco). */
    protected String login(String login, String senha) throws Exception {
        String body = tentarLogin(login, senha).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    protected static RequestPostProcessor bearer(String token) {
        return request -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }

    /** CPF aleatório com dígitos verificadores válidos. */
    protected static String cpfAleatorio() {
        int[] d = new int[11];
        do {
            for (int i = 0; i < 9; i++) {
                d[i] = ThreadLocalRandom.current().nextInt(10);
            }
        } while (d[0] == d[1] && d[1] == d[2]);
        for (int t = 9; t <= 10; t++) {
            int soma = 0;
            for (int i = 0; i < t; i++) {
                soma += d[i] * (t + 1 - i);
            }
            int resto = (soma * 10) % 11;
            d[t] = resto == 10 ? 0 : resto;
        }
        StringBuilder sb = new StringBuilder();
        for (int x : d) {
            sb.append(x);
        }
        return sb.toString();
    }

    protected static String telefoneAleatorio() {
        return "119" + ThreadLocalRandom.current().nextLong(10_000_000L, 99_999_999L);
    }

    protected long criarFuncionario(long usuarioId, String nome) throws Exception {
        String body = mvc.perform(post("/api/v1/funcionarios").with(comPapel("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usuarioId": %d, "nome": "%s", "cpf": "%s", "sexo": "FEMININO", "telefone": "%s",
                                 "endereco": {"cep": "13610000", "logradouro": "Rua Exemplo", "numero": "100",
                                              "bairro": "Centro", "cidade": "Leme", "uf": "SP"}}"""
                                .formatted(usuarioId, nome, cpfAleatorio(), telefoneAleatorio())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    /** Etapa 06: abre o caixa do dono do token (fundo 0). Já aberto (409) conta como sucesso. */
    protected void abrirCaixa(String token) throws Exception {
        int st = mvc.perform(post("/api/v1/caixas/abrir").with(bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fundoAbertura\": 0}")).andReturn().getResponse().getStatus();
        if (st != 201 && st != 409) {
            throw new IllegalStateException("Não abriu o caixa: HTTP " + st);
        }
    }
}
