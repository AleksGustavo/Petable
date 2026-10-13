package br.com.tecnodreamer.petconnect.usuario.web;

import com.jayway.jsonpath.JsonPath;
import br.com.tecnodreamer.petconnect.pet.web.PetIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UsuarioControllerIT extends PetIntegrationTest {

    private static final String URL = "/api/v1/usuarios";

    private long adminBootstrapId() {
        return jdbc.queryForObject("select id from usuario where username = ?", Long.class, ADMIN_USER);
    }

    private static String papelJson(String papel) {
        return "{\"papel\": \"" + papel + "\"}";
    }

    @Test
    void adminDeveListarUsuarios() throws Exception {
        String username = nomeUsuario();
        long id = criarUsuario(username, "SERVICO");
        criarFuncionario(id, "Funcionária " + username);

        mvc.perform(get(URL).param("q", username).with(comPapel("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value(username))
                .andExpect(jsonPath("$.content[0].papel").value("SERVICO"))
                .andExpect(jsonPath("$.content[0].funcionario.nome").value("Funcionária " + username))
                .andExpect(jsonPath("$.content[0].senhaHash").doesNotExist())
                .andExpect(jsonPath("$.content[0].tokenVersion").doesNotExist());
        mvc.perform(get(URL).param("q", "Funcionária " + username).with(comPapel("ADMIN")))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(URL).param("papel", "SERVICO").param("ativo", "true").param("size", "100").with(comPapel("ADMIN")))
                .andExpect(jsonPath("$.content[*].username", hasItem(username)));
    }

    @Test
    void vendasNaoDeveListarUsuarios() throws Exception {
        mvc.perform(get(URL).with(comPapel("VENDAS"))).andExpect(status().isForbidden());
    }

    @Test
    void servicoNaoDeveCriarUsuario() throws Exception {
        mvc.perform(post(URL).with(comPapel("SERVICO")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminDeveCriarUsuario() throws Exception {
        String username = nomeUsuario();
        mvc.perform(post(URL).with(comPapel("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "email": "%s@petconnect.test", "senha": "%s", "papel": "VENDAS"}"""
                                .formatted(username, username, SENHA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("senha"))));

        mvc.perform(post(URL).with(comPapel("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "email": "%s@petconnect.test", "senha": "fraca", "papel": "VENDAS"}"""
                                .formatted(nomeUsuario(), nomeUsuario())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.senha").exists());

        long id = jdbc.queryForObject("select id from usuario where username = ?", Long.class, username);
        assertThat(acoesAuditadas("usuario", id)).contains("USUARIO_CRIADO");
        assertThat(jdbc.queryForObject("select senha_hash from usuario where id = ?", String.class, id)).startsWith("$2a$12$");
    }

    @Test
    void deveRetornar409ParaUsernameDuplicado() throws Exception {
        String username = nomeUsuario();
        criarUsuario(username, "VENDAS");
        mvc.perform(post(URL).with(comPapel("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "email": "outro-%s@petconnect.test", "senha": "%s", "papel": "VENDAS"}"""
                                .formatted(username.toUpperCase(), username, SENHA)))
                .andExpect(status().isConflict());
    }

    @Test
    void deveRetornar409ParaEmailDuplicado() throws Exception {
        String username = nomeUsuario();
        criarUsuario(username, "VENDAS");
        mvc.perform(post(URL).with(comPapel("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "email": "%s@petconnect.test", "senha": "%s", "papel": "VENDAS"}"""
                                .formatted(nomeUsuario(), username, SENHA)))
                .andExpect(status().isConflict());
    }

    @Test
    void adminDeveAlterarPapel() throws Exception {
        long id = criarUsuario(nomeUsuario(), "VENDAS");

        mvc.perform(patch(URL + "/" + id + "/papel").with(comPapel("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(papelJson("SERVICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.papel").value("SERVICO"));
        assertThat(jdbc.queryForObject("select token_version from usuario where id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(acoesAuditadas("usuario", id)).contains("USUARIO_PAPEL_ALTERADO");
    }

    @Test
    void deveImpedirRebaixamentoDoUltimoAdmin() throws Exception {
        // Deixa só o ADMIN do bootstrap ativo (outros testes podem ter criado ADMINs).
        jdbc.update("update usuario u join papel p on p.id = u.papel_id set u.ativo = false "
                + "where p.nome = 'ADMIN' and u.username <> ?", ADMIN_USER);

        mvc.perform(patch(URL + "/" + adminBootstrapId() + "/papel").with(comPapel("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(papelJson("VENDAS")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Não é possível remover ou desativar o último administrador ativo do sistema."));
    }

    @Test
    void deveImpedirDesativacaoDoUltimoAdmin() throws Exception {
        jdbc.update("update usuario u join papel p on p.id = u.papel_id set u.ativo = false "
                + "where p.nome = 'ADMIN' and u.username <> ?", ADMIN_USER);

        mvc.perform(patch(URL + "/" + adminBootstrapId() + "/desativar").with(comPapel("ADMIN")))
                .andExpect(status().isConflict());
        tentarLogin(ADMIN_USER, ADMIN_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void usuarioDesativadoNaoDeveConseguirLogin() throws Exception {
        String username = nomeUsuario();
        long id = criarUsuario(username, "VENDAS");
        mvc.perform(patch(URL + "/" + id + "/desativar").with(comPapel("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(false));

        tentarLogin(username, SENHA).andExpect(status().isUnauthorized());

        mvc.perform(patch(URL + "/" + id + "/ativar").with(comPapel("ADMIN"))).andExpect(jsonPath("$.ativo").value(true));
        tentarLogin(username, SENHA).andExpect(status().isOk());
        assertThat(acoesAuditadas("usuario", id)).contains("USUARIO_DESATIVADO", "USUARIO_ATIVADO");
    }

    @Test
    void tokenAnteriorDeveFalharAposMudancaDePapel() throws Exception {
        String username = nomeUsuario();
        long id = criarUsuario(username, "VENDAS");
        String token = login(username, SENHA);
        mvc.perform(get("/api/v1/auth/me").with(bearer(token))).andExpect(status().isOk())
                .andExpect(jsonPath("$.papel").value("VENDAS"));

        mvc.perform(patch(URL + "/" + id + "/papel").with(comPapel("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content(papelJson("SERVICO"))).andExpect(status().isOk());

        mvc.perform(get("/api/v1/auth/me").with(bearer(token))).andExpect(status().isUnauthorized());
        String novo = login(username, SENHA);
        mvc.perform(get("/api/v1/auth/me").with(bearer(novo))).andExpect(jsonPath("$.papel").value("SERVICO"));
    }

    @Test
    void tokenAnteriorDeveFalharAposDesativacao() throws Exception {
        String username = nomeUsuario();
        long id = criarUsuario(username, "VENDAS");
        String token = login(username, SENHA);

        mvc.perform(patch(URL + "/" + id + "/desativar").with(comPapel("ADMIN"))).andExpect(status().isOk());

        mvc.perform(get("/api/v1/clientes").with(bearer(token))).andExpect(status().isUnauthorized());
    }

    @Test
    void adminDeveEditarContaSemTocarSenhaOuPapel() throws Exception {
        String username = nomeUsuario();
        long id = criarUsuario(username, "VENDAS");
        String novo = nomeUsuario();

        String body = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(URL + "/" + id)
                        .with(comPapel("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "email": "%s@petconnect.test", "papel": "ADMIN", "senha": "Outra@Senha2026"}"""
                                .formatted(novo, novo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(novo))
                .andExpect(jsonPath("$.papel").value("VENDAS"))
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(body, "$.email")).isEqualTo(novo + "@petconnect.test");
        tentarLogin(novo, SENHA).andExpect(status().isOk());
        assertThat(acoesAuditadas("usuario", id)).contains("USUARIO_ATUALIZADO");
    }
}
