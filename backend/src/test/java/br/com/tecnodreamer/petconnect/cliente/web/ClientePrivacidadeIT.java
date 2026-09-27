package br.com.tecnodreamer.petconnect.cliente.web;

import br.com.tecnodreamer.petconnect.venda.web.VendaIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClientePrivacidadeIT extends VendaIntegrationTest {

    private long clienteComHistorico() throws Exception {
        long cliente = criarCliente(unico("Titular"));
        jdbc.update("UPDATE cliente SET cpf = ?, email = ?, observacoes = ? WHERE id = ?", cpfAleatorio(),
                "titular@exemplo.test", "prefere banho às terças", cliente);
        long pet = criarPet(cliente, "Bidu", "Cão");
        jdbc.update("UPDATE pet SET observacoes = 'alérgico' WHERE id = ?", pet);
        long venda = venderOk(vendas(), vendaJson("PIX", "0", "null", produto("20.00", 5), 1));
        jdbc.update("UPDATE venda SET cliente_id = ? WHERE id = ?", cliente, venda);
        return cliente;
    }

    @Test
    void exportacaoTrazTudoSobreOTitular() throws Exception {
        long cliente = clienteComHistorico();

        mvc.perform(get("/api/v1/clientes/{id}/dados-pessoais", cliente).with(comPapel("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"dados-cliente-" + cliente + ".json\""))
                .andExpect(jsonPath("$.cliente.email").value("titular@exemplo.test"))
                .andExpect(jsonPath("$.pets[0].nome").value("Bidu"))
                .andExpect(jsonPath("$.vendas.length()").value(1));
        assertThat(acoesAuditadas("cliente", cliente)).contains("CLIENTE_DADOS_EXPORTADOS");
    }

    @Test
    void vendasNaoExportaNemAnonimiza() throws Exception {
        long cliente = criarCliente(unico("Titular"));
        mvc.perform(get("/api/v1/clientes/{id}/dados-pessoais", cliente).with(comPapel("VENDAS")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/clientes/{id}/anonimizar", cliente).with(comPapel("VENDAS"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\": \"Pedido do titular\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonimizacaoPreservaHistoricoEENaoRepete() throws Exception {
        long cliente = clienteComHistorico();
        String nomeAntigo = jdbc.queryForObject("SELECT nome FROM cliente WHERE id = ?", String.class, cliente);

        mvc.perform(post("/api/v1/clientes/{id}/anonimizar", cliente).with(comPapel("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\": \"Pedido do titular por e-mail\"}"))
                .andExpect(status().isNoContent());

        Map<String, Object> c = jdbc.queryForMap("SELECT * FROM cliente WHERE id = ?", cliente);
        assertThat(c.get("nome")).isEqualTo("Cliente anonimizado #" + cliente);
        assertThat(c.get("cpf")).isNull();
        assertThat(c.get("email")).isNull();
        assertThat(c.get("observacoes")).isNull();
        assertThat(c.get("ativo")).isEqualTo(false);
        assertThat(((String) c.get("telefone"))).startsWith("0").hasSize(11);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM venda WHERE cliente_id = ?", Long.class, cliente)).isEqualTo(1L);
        assertThat(jdbc.queryForList("SELECT observacoes FROM pet WHERE cliente_id = ?", String.class, cliente))
                .containsOnlyNulls();
        assertThat(acoesAuditadas("cliente", cliente)).contains("CLIENTE_ANONIMIZADO");

        String busca = mvc.perform(get("/api/v1/clientes").param("busca", nomeAntigo).with(comPapel("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Object> ids = JsonPath.read(busca, "$.content[*].id");
        assertThat(ids).doesNotContain((int) cliente);

        mvc.perform(post("/api/v1/clientes/{id}/anonimizar", cliente).with(comPapel("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\": \"Pedido repetido\"}"))
                .andExpect(status().isConflict());
    }
}
