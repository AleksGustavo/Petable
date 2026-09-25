package br.com.tecnodreamer.petconnect.cliente.web;

import br.com.tecnodreamer.petconnect.IntegrationTest;
import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClienteControllerIT extends IntegrationTest {

    private static final String URL = "/api/v1/clientes";

    @Autowired
    MockMvc mvc;

    @Autowired
    AuditoriaRepository auditoria;

    private static RequestPostProcessor comPapel(String papel) {
        return jwt().jwt(j -> j.subject("teste-" + papel.toLowerCase()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + papel));
    }

    private static String json(String nome, String cpf, String telefone) {
        return """
                {
                  "nome": "%s",
                  "cpf": %s,
                  "telefone": "%s",
                  "email": "cliente@exemplo.com",
                  "endereco": {
                    "cep": "13616-140", "logradouro": "Rua Evaristo Harder", "numero": "426",
                    "bairro": "Jardim Primavera", "cidade": "Leme", "uf": "sp"
                  }
                }""".formatted(nome, cpf == null ? "null" : "\"" + cpf + "\"", telefone);
    }

    private long criar(String nome, String cpf, String telefone) throws Exception {
        String body = mvc.perform(post(URL).with(comPapel("VENDAS"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(nome, cpf, telefone)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll("^\\{\"id\":(\\d+),.*$", "$1"));
    }

    @Test
    void cicloCompletoComEnderecoNormalizado() throws Exception {
        mvc.perform(post(URL).with(comPapel("VENDAS"))
                        .contentType(MediaType.APPLICATION_JSON).content(json("Maria Souza", "111.444.777-35", "(19) 99111-2222")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.cpf").value("11144477735"))
                .andExpect(jsonPath("$.telefone").value("19991112222"))
                .andExpect(jsonPath("$.endereco.cep").value("13616140"))
                .andExpect(jsonPath("$.endereco.uf").value("SP"));

        mvc.perform(get(URL).param("busca", "111.444").with(comPapel("SERVICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].nome").value("Maria Souza"));
    }

    @Test
    void atualizarTrocaDadosEEndereco() throws Exception {
        long id = criar("João Lima", null, "19991113333");
        String alterado = json("João P. Lima", null, "19991113333").replace("Leme", "Araras");

        mvc.perform(put(URL + "/" + id).with(comPapel("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(alterado))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("João P. Lima"))
                .andExpect(jsonPath("$.endereco.cidade").value("Araras"));
    }

    @Test
    void cpfDuplicadoResponde409() throws Exception {
        criar("Primeiro", "529.982.247-25", "19991114444");

        mvc.perform(post(URL).with(comPapel("VENDAS"))
                        .contentType(MediaType.APPLICATION_JSON).content(json("Segundo", "52998224725", "19991115555")))
                .andExpect(status().isConflict());
    }

    @Test
    void cpfInvalidoResponde400ComCampo() throws Exception {
        mvc.perform(post(URL).with(comPapel("VENDAS"))
                        .contentType(MediaType.APPLICATION_JSON).content(json("X", "123.456.789-00", "19991116666")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.cpf").value("CPF inválido"));
    }

    @Test
    void papelServicoNaoPodeCadastrar() throws Exception {
        mvc.perform(post(URL).with(comPapel("SERVICO"))
                        .contentType(MediaType.APPLICATION_JSON).content(json("Y", null, "19991117777")))
                .andExpect(status().isForbidden());
    }

    @Test
    void semTokenResponde401() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void inativarEscondeClienteERegistraAuditoriaNoMongo() throws Exception {
        long id = criar("Para Inativar", null, "19991118888");

        mvc.perform(delete(URL + "/" + id).with(comPapel("VENDAS"))).andExpect(status().isNoContent());
        mvc.perform(get(URL + "/" + id).with(comPapel("VENDAS"))).andExpect(status().isNotFound());

        var eventos = auditoria.findByEntidadeAndEntidadeId("cliente", String.valueOf(id), Pageable.unpaged());
        assertThat(eventos.map(e -> e.acao()).toList()).containsExactlyInAnyOrder("CRIAR", "INATIVAR");
        assertThat(eventos.getContent()).allSatisfy(e -> assertThat(e.usuario()).isEqualTo("teste-vendas"));
    }
}
