package br.com.tecnodreamer.petconnect.shared.modulos;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Instalação com SÓ {@code produtos, caixa, pdv} (sem estoque, sem lotes, sem financeiro, sem o resto). A venda continua baixando saldo e
 * lançando a receita pelo núcleo técnico; o inventário e tudo que não foi contratado ficam indisponíveis para qualquer papel.
 */
@TestPropertySource(properties = "petconnect.modulos=produtos,caixa,pdv")
class ModulosSemEstoqueIT extends ModulosIntegrationTest {

    @Test
    void capacidadesMostramSoOQueFoiContratado() throws Exception {
        consultar("/api/v1/modulos", admin()).andExpect(status().isOk())
                .andExpect(jsonPath("$.todos").value(false))
                .andExpect(jsonPath("$.modulos.length()").value(3))
                .andExpect(jsonPath("$.modulos[0]").value("caixa"))
                .andExpect(jsonPath("$.modulos[1]").value("pdv"))
                .andExpect(jsonPath("$.modulos[2]").value("produtos"))
                .andExpect(jsonPath("$.integracoes.length()").value(0))
                .andExpect(jsonPath("$.relatorios.length()").value(0));
        // Qualquer usuário autenticado lê as capacidades; sem token, 401.
        mvc.perform(get("/api/v1/modulos").with(comPapel("SERVICO"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/modulos")).andExpect(status().isUnauthorized());
    }

    @Test
    void semEstoqueMasComPdvAVendaContinuaBaixandoSaldoELancandoAReceita() throws Exception {
        long p = produto("10.00", 0);
        semearSaldo(p, 5);
        long lancamentosAntes = jdbc.queryForObject("select count(*) from movimentacao_financeira", Long.class);

        long venda = venderOk(admin(), vendaJson("PIX", "0.00", "null", p, 2));

        assertThat(saldo(p)).isEqualTo(3);
        assertThat(movimentacoes(p, "SAIDA_VENDA")).isEqualTo(1);
        // O núcleo técnico grava a receita mesmo sem o módulo financeiro.
        assertThat(jdbc.queryForObject("select count(*) from movimentacao_financeira", Long.class)).isEqualTo(lancamentosAntes + 1);
        assertThat(jdbc.queryForObject("select count(*) from movimentacao_financeira where chave_origem = ?", Integer.class, "VENDA:" + venda)).isEqualTo(1);
        // O cancelamento também recompõe o saldo.
        cancelar(admin(), venda, "Cancelamento sem o módulo de estoque").andExpect(status().isOk());
        assertThat(saldo(p)).isEqualTo(5);
    }

    @Test
    void inventarioEGestaoDeEstoqueFicamIndisponiveisParaTodosOsPapeis() throws Exception {
        long p = produto("10.00", 0);
        semearSaldo(p, 4);
        String ajuste = "/api/v1/produtos/" + p + "/ajustes-estoque";
        String corpo = "{\"quantidade\": 1, \"motivo\": \"Tentativa de ajuste\"}";

        for (String token : new String[] {admin(), vendas()}) {
            exigirIndisponivel(mvc.perform(post(ajuste).with(bearer(token)).contentType(MediaType.APPLICATION_JSON).content(corpo)));
            exigirIndisponivel(consultar("/api/v1/produtos/" + p + "/movimentacoes", token));
            exigirIndisponivel(consultar("/api/v1/produtos/resumo-estoque", token));
            exigirIndisponivel(consultar("/api/v1/estoque/reconciliacao", token));
        }
        exigirIndisponivel(mvc.perform(post(ajuste).with(comPapel("SERVICO")).contentType(MediaType.APPLICATION_JSON).content(corpo)));
        assertThat(saldo(p)).as("nenhum ajuste passou").isEqualTo(4);
        // O que foi contratado segue normal: o cadastro do produto lê o saldo.
        consultar("/api/v1/produtos/" + p, admin()).andExpect(status().isOk()).andExpect(jsonPath("$.quantidadeEstoque").value(4));
    }

    @Test
    void lotesComprasFinanceiroClientesPetsAgendaERelatoriosNaoExistemNestaInstalacao() throws Exception {
        String admin = admin();
        for (String url : new String[] {"/api/v1/lotes", "/api/v1/estoque/alertas", "/api/v1/compras", "/api/v1/fornecedores", "/api/v1/financeiro/lancamentos",
                "/api/v1/financeiro/contas-a-pagar", "/api/v1/clientes", "/api/v1/pets", "/api/v1/agendamentos", "/api/v1/servicos",
                "/api/v1/relatorios/vendas", "/api/v1/relatorios/vendas/csv", "/api/v1/especies"}) {
            exigirIndisponivel(consultar(url, admin));
        }
        // O núcleo nunca depende de módulo.
        consultar("/api/v1/auth/me", admin).andExpect(status().isOk());
        consultar("/api/v1/usuarios", admin).andExpect(status().isOk());
        consultar("/api/v1/me", admin).andExpect(status().isOk());
        consultar("/api/v1/funcionarios", admin).andExpect(status().isOk());
    }

    @Test
    void semTokenContinuaSendo401ENaoRevelaQuaisModulosExistem() throws Exception {
        mvc.perform(get("/api/v1/financeiro/lancamentos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/compras")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/produtos")).andExpect(status().isUnauthorized());
    }

    @Test
    void alterarOFrontendOuUsarCurlNaoReabilitaOEndpoint() throws Exception {
        String admin = admin();
        // Cabeçalhos, parâmetros, cookie e até uma claim de módulos no token: nada disso é fonte de verdade.
        mvc.perform(get("/api/v1/compras").with(bearer(admin)).header("X-Modulos", "compras,financeiro,estoque").header("X-Module", "*")
                .header("X-Forwarded-Modulos", "todos").param("modulos", "todos").param("modulo", "compras").cookie(new jakarta.servlet.http.Cookie("modulos", "todos")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("MODULO_INDISPONIVEL"));
        mvc.perform(get("/api/v1/financeiro/lancamentos").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(j -> j.claim("modulos", java.util.List.of("financeiro", "todos")).claim("roles", java.util.List.of("ADMIN")))
                        .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("MODULO_INDISPONIVEL"));
        mvc.perform(post("/api/v1/financeiro/lancamentos").with(bearer(admin)).contentType(MediaType.APPLICATION_JSON).header("X-Modulos", "financeiro")
                .content("{\"modulos\": [\"financeiro\"], \"tipo\": \"ENTRADA\"}")).andExpect(status().isForbidden());
    }

    @Test
    void variacoesDeCaminhoMetodoEBarraFinalNaoEscapam() throws Exception {
        String admin = admin();
        for (String url : new String[] {"/api/v1/%63ompras", "/api/v1/%66inanceiro/lancamentos", "/api/v1/compras/", "/api/v1/financeiro/lancamentos/"}) {
            assertThat(mvc.perform(get(java.net.URI.create(url)).with(bearer(admin))).andReturn().getResponse().getStatus()).as(url).isEqualTo(403);
        }
        mvc.perform(head("/api/v1/estoque/alertas").with(bearer(admin))).andExpect(status().isForbidden());
        mvc.perform(head("/api/v1/lotes").with(bearer(admin))).andExpect(status().isForbidden());
        // Ponto e vírgula e '..' são barrados pelo firewall do Spring Security antes mesmo do filtro (400), nunca chegam a um controller.
        for (String url : new String[] {"/api/v1/compras;x=1", "/api/v1/produtos/../compras"}) {
            try {
                int s = mvc.perform(get(url).with(bearer(admin))).andReturn().getResponse().getStatus();
                assertThat(s).as(url).isIn(400, 403, 404);
            } catch (Exception rejeitada) {
                assertThat(rejeitada.toString()).as(url).containsIgnoringCase("rejected");
            }
        }
    }
}
