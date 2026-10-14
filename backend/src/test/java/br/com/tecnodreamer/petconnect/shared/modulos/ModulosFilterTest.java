package br.com.tecnodreamer.petconnect.shared.modulos;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

/** O filtro de módulo isolado: decisão por requisição, caminho decodificado e comportamento para quem não está autenticado. */
class ModulosFilterTest {

    private final ModulosAtivos modulos = new ModulosAtivos("produtos,estoque,caixa,pdv", CatalogoModulos.carregar());
    private final ModulosFilter filtro = new ModulosFilter(modulos);

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private void logado() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin", "x", AuthorityUtils.createAuthorityList("ROLE_ADMIN")));
    }

    private MockHttpServletResponse chamar(String metodo, String uri) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(metodo, uri);
        req.setRequestURI(uri);
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filtro.doFilter(req, res, chain);
        res.setCharacterEncoding("UTF-8");
        return res;
    }

    private boolean passou(MockHttpServletResponse r) throws Exception {
        return r.getStatus() == HttpServletResponse.SC_OK && r.getContentAsString().isEmpty();
    }

    @Test
    void moduloContratadoPassa() throws Exception {
        logado();
        assertThat(passou(chamar("POST", "/api/v1/vendas"))).isTrue();
        assertThat(passou(chamar("GET", "/api/v1/produtos/12"))).isTrue();
        assertThat(passou(chamar("GET", "/api/v1/auth/me"))).isTrue();
    }

    @Test
    void moduloNaoContratadoRecebe403ComCodigoUniformeESemDizerQualModulo() throws Exception {
        logado();
        MockHttpServletResponse r = chamar("GET", "/api/v1/financeiro/lancamentos");
        assertThat(r.getStatus()).isEqualTo(403);
        assertThat(r.getContentType()).startsWith("application/problem+json");
        assertThat(r.getContentAsString()).contains("\"codigo\":\"MODULO_INDISPONIVEL\"").contains("\"status\":403")
                .doesNotContain("financeiro");
        // A mesma resposta para qualquer módulo fora da lista.
        assertThat(chamar("GET", "/api/v1/compras").getContentAsString()).isEqualTo(r.getContentAsString());
    }

    @Test
    void quemNaoEstaAutenticadoNaoRecebeNadaDoFiltroOAnonimoVira401NoSecurity() throws Exception {
        // Sem autenticação o filtro não decide (o 401 vem do Security, sem revelar quais módulos existem).
        assertThat(passou(chamar("GET", "/api/v1/financeiro/lancamentos"))).isTrue();
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("k", "anonimo", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertThat(passou(chamar("GET", "/api/v1/financeiro/lancamentos"))).isTrue();
    }

    @Test
    void caminhoCodificadoNaoEscapa() throws Exception {
        logado();
        // %66 = f: o MVC roteia para /api/v1/financeiro; o filtro precisa enxergar o mesmo caminho.
        assertThat(chamar("GET", "/api/v1/%66inanceiro/lancamentos").getStatus()).isEqualTo(403);
        assertThat(chamar("GET", "/api/v1/%63ompras").getStatus()).isEqualTo(403);
        assertThat(chamar("POST", "/api/v1/produtos/9/%63ontrole-lote").getStatus()).isEqualTo(403);
    }

    @Test
    void barraFinalConteudoComPontoEVirgulaEHeadNaoEscapam() throws Exception {
        logado();
        assertThat(chamar("GET", "/api/v1/financeiro/").getStatus()).isEqualTo(403);
        assertThat(chamar("GET", "/api/v1/compras/").getStatus()).isEqualTo(403);
        assertThat(chamar("GET", "/api/v1/compras;x=1").getStatus()).isEqualTo(403);
        assertThat(chamar("HEAD", "/api/v1/estoque/alertas").getStatus()).isEqualTo(403);
        assertThat(chamar("HEAD", "/api/v1/lotes").getStatus()).isEqualTo(403);
        assertThat(chamar("DELETE", "/api/v1/estoque/alertas").getStatus()).isEqualTo(403);
    }

    @Test
    void relatorioEExportacaoIndiretosSeguemOModuloDeOrigem() throws Exception {
        ModulosFilter comRelatorios = new ModulosFilter(new ModulosAtivos("produtos,estoque,caixa,pdv,relatorios", CatalogoModulos.carregar()));
        logado();
        for (String uri : new String[] {"/api/v1/relatorios/contas", "/api/v1/relatorios/contas/csv", "/api/v1/relatorios/despesas/csv"}) {
            MockHttpServletResponse r = new MockHttpServletResponse();
            comRelatorios.doFilter(new MockHttpServletRequest("GET", uri) {{ setRequestURI(uri); }}, r, new MockFilterChain());
            assertThat(r.getStatus()).as(uri).isEqualTo(403);
        }
        MockHttpServletResponse ok = new MockHttpServletResponse();
        comRelatorios.doFilter(new MockHttpServletRequest("GET", "/api/v1/relatorios/vendas/csv") {{ setRequestURI("/api/v1/relatorios/vendas/csv"); }}, ok, new MockFilterChain());
        assertThat(ok.getStatus()).isEqualTo(200);
    }

    @Test
    void forasDaApiNaoSaoDoFiltro() throws Exception {
        logado();
        assertThat(passou(chamar("GET", "/actuator/health"))).isTrue();
        assertThat(passou(chamar("GET", "/v3/api-docs"))).isTrue();
    }
}
