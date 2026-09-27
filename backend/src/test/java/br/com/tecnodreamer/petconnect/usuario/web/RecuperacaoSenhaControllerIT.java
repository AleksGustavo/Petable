package br.com.tecnodreamer.petconnect.usuario.web;

import br.com.tecnodreamer.petconnect.pet.web.PetIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecuperacaoSenhaControllerIT extends PetIntegrationTest {

    private static final String NOVA = "Nova@Senha2026";
    private static final String NEUTRA = "Se existir uma conta ativa para o e-mail informado, enviaremos as instruções de redefinição.";

    /** IP diferente por pedido: o limite por IP (10/30 min) não interfere entre testes. */
    private ResultActions esqueci(String email) throws Exception {
        String ip = "10." + ThreadLocalRandom.current().nextInt(256) + "." + ThreadLocalRandom.current().nextInt(256) + ".1";
        return mvc.perform(post("/api/v1/auth/esqueci-senha").header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\": \"" + email + "\"}"));
    }

    private ResultActions redefinir(String token, String senha) throws Exception {
        return mvc.perform(post("/api/v1/auth/redefinir-senha").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token": "%s", "novaSenha": "%s", "confirmacaoNovaSenha": "%s"}""".formatted(token, senha, senha)));
    }

    private String usuarioComToken() throws Exception {
        String username = nomeUsuario();
        criarUsuario(username, "VENDAS");
        esqueci(username + "@petconnect.test").andExpect(status().isAccepted());
        return username;
    }

    private String token(String username) {
        return emails.ultimoToken(username + "@petconnect.test").orElseThrow();
    }

    @Test
    void deveResponder202ParaEmailExistente() throws Exception {
        String username = nomeUsuario();
        criarUsuario(username, "VENDAS");

        esqueci(username + "@petconnect.test").andExpect(status().isAccepted()).andExpect(jsonPath("$.message").value(NEUTRA));
        assertThat(emails.para(username + "@petconnect.test")).hasSize(1);
    }

    @Test
    void deveResponder202ParaEmailInexistente() throws Exception {
        esqueci("ninguem-" + nomeUsuario() + "@petconnect.test").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(NEUTRA));
    }

    @Test
    void deveResponder202ParaUsuarioInativo() throws Exception {
        String username = nomeUsuario();
        long id = criarUsuario(username, "VENDAS");
        mvc.perform(patch("/api/v1/usuarios/" + id + "/desativar").with(comPapel("ADMIN"))).andExpect(status().isOk());

        esqueci(username + "@petconnect.test").andExpect(status().isAccepted()).andExpect(jsonPath("$.message").value(NEUTRA));
        assertThat(emails.para(username + "@petconnect.test")).isEmpty();
    }

    @Test
    void deveCriarTokenHashParaUsuarioAtivo() throws Exception {
        String username = usuarioComToken();
        String token = token(username);

        String hash = jdbc.queryForObject("select t.token_hash from token_redefinicao_senha t join usuario u on u.id = t.usuario_id "
                + "where u.username = ? and t.usado_em is null", String.class, username);
        String esperado = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        assertThat(hash).isEqualTo(esperado).isNotEqualTo(token);
        assertThat(jdbc.queryForObject("select count(*) from token_redefinicao_senha where token_hash = ?", Integer.class, token)).isZero();
    }

    @Test
    void deveCompletarFluxoDeRedefinicao() throws Exception {
        String username = usuarioComToken();
        String tokenJwt = login(username, SENHA);

        redefinir(token(username), NOVA).andExpect(status().isNoContent());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/me").with(bearer(tokenJwt)))
                .andExpect(status().isUnauthorized());
        long id = jdbc.queryForObject("select id from usuario where username = ?", Long.class, username);
        assertThat(acoesAuditadas("usuario", id)).contains("REDEFINICAO_SENHA_SOLICITADA", "SENHA_REDEFINIDA");
    }

    @Test
    void deveRejeitarTokenExpirado() throws Exception {
        String username = usuarioComToken();
        jdbc.update("update token_redefinicao_senha t join usuario u on u.id = t.usuario_id "
                + "set t.expira_em = date_sub(now(6), interval 1 day) where u.username = ?", username);

        redefinir(token(username), NOVA).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.token").value("O link de redefinição é inválido ou expirou."));
    }

    @Test
    void deveRejeitarTokenUsado() throws Exception {
        String username = usuarioComToken();
        String token = token(username);
        redefinir(token, NOVA).andExpect(status().isNoContent());

        redefinir(token, "Outra@Senha2026").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.token").exists());
    }

    @Test
    void novoPedidoInvalidaOAnterior() throws Exception {
        String username = usuarioComToken();
        String primeiro = token(username);
        esqueci(username + "@petconnect.test").andExpect(status().isAccepted());

        redefinir(primeiro, NOVA).andExpect(status().isBadRequest());
        redefinir(token(username), NOVA).andExpect(status().isNoContent());
    }

    @Test
    void loginComNovaSenhaDeveFuncionarAposRedefinicao() throws Exception {
        String username = usuarioComToken();
        redefinir(token(username), NOVA).andExpect(status().isNoContent());

        tentarLogin(username, NOVA).andExpect(status().isOk());
    }

    @Test
    void loginComSenhaAntigaDeveFalharAposRedefinicao() throws Exception {
        String username = usuarioComToken();
        redefinir(token(username), NOVA).andExpect(status().isNoContent());

        tentarLogin(username, SENHA).andExpect(status().isUnauthorized());
    }
}
