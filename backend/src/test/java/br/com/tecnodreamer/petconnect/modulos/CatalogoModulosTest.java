package br.com.tecnodreamer.petconnect.modulos;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Estrutura e coerência do catálogo de módulos (etapa 06): ids, dependências, pacotes e bloqueios de remoção. */
class CatalogoModulosTest {

    private final CatalogoModulos c = new CatalogoModulos();

    @Test
    void idsSaoUnicosEOStatusEValido() {
        List<String> ids = c.idsDosModulos();
        assertThat(ids).doesNotHaveDuplicates().doesNotContain("nucleo");
        for (JsonNode m : c.raiz.get("modulos")) {
            assertThat(m.get("status").asText()).as(m.get("id").asText()).isIn("EXISTE", "PARCIAL");
            assertThat(m.get("nome").asText()).isNotBlank();
        }
        List<String> integracoes = new ArrayList<>();
        c.raiz.get("integracoes").forEach(i -> integracoes.add(i.get("id").asText()));
        assertThat(integracoes).doesNotHaveDuplicates().doesNotContainAnyElementsOf(ids);
    }

    @Test
    void dependenciasReferenciamModulosExistentesESemCiclo() {
        List<String> ids = c.idsDosModulos();
        for (String id : ids) {
            JsonNode m = c.modulo(id);
            assertThat(ids).as("requer de " + id).containsAll(CatalogoModulos.textos(m, "requer"));
            assertThat(ids).as("recomenda de " + id).containsAll(CatalogoModulos.textos(m, "recomenda"));
            assertThat(CatalogoModulos.textos(m, "requer")).as("auto-dependência de " + id).doesNotContain(id);
            if (m.has("condicionais")) {
                m.get("condicionais").forEach(x -> assertThat(ids).contains(x.get("requer").asText()));
            }
        }
        // Sem ciclo no grafo de `requer`.
        for (String id : ids) {
            assertThat(alcanca(id, id, new HashSet<>())).as("ciclo passando por " + id).isFalse();
        }
    }

    private boolean alcanca(String origem, String atual, Set<String> visitados) {
        for (String dep : CatalogoModulos.textos(c.modulo(atual), "requer")) {
            if (dep.equals(origem)) {
                return true;
            }
            if (visitados.add(dep) && alcanca(origem, dep, visitados)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void regrasConhecidasDeDependenciaEstaoNoCatalogo() {
        // Achados do código real (ver 06_CATALOGO_MODULOS.md): o caixa é exigido por venda, atendimento e pagamento de conta.
        assertThat(CatalogoModulos.textos(c.modulo("pdv"), "requer")).contains("caixa", "produtos");
        assertThat(CatalogoModulos.textos(c.modulo("agenda"), "requer")).contains("caixa", "pets");
        assertThat(CatalogoModulos.textos(c.modulo("financeiro"), "requer")).contains("caixa");
        assertThat(CatalogoModulos.textos(c.modulo("pets"), "requer")).contains("clientes");
        assertThat(CatalogoModulos.textos(c.modulo("clinica"), "requer")).contains("pets");
        assertThat(CatalogoModulos.textos(c.modulo("lotes"), "requer")).contains("estoque");
        assertThat(CatalogoModulos.textos(c.modulo("estoque"), "requer")).contains("produtos");
        assertThat(c.modulo("clinica").get("status").asText()).isEqualTo("PARCIAL");
        // Relatório só existe com o módulo de origem.
        JsonNode porRelatorio = c.modulo("relatorios").get("porRelatorio");
        assertThat(porRelatorio.get("vendas").get(0).asText()).isEqualTo("pdv");
        assertThat(porRelatorio.get("servicos").get(0).asText()).isEqualTo("agenda");
        porRelatorio.forEach(origens -> origens.forEach(o -> assertThat(c.idsDosModulos()).contains(o.asText())));
    }

    @Test
    void pacotesFecham_asDependenciasEOCompletoTemTodosOsModulos() {
        Set<String> idsDePacotes = new HashSet<>();
        for (JsonNode p : c.raiz.get("pacotes")) {
            String id = p.get("id").asText();
            assertThat(idsDePacotes.add(id)).as("pacote repetido " + id).isTrue();
            List<String> modulos = CatalogoModulos.textos(p, "modulos");
            assertThat(modulos).as("módulos de " + id).doesNotHaveDuplicates().isSubsetOf(c.idsDosModulos());
            for (String m : modulos) {
                assertThat(modulos).as("o pacote " + id + " inclui " + m + " e precisa incluir o que ele requer")
                        .containsAll(CatalogoModulos.textos(c.modulo(m), "requer"));
            }
            if (!p.get("pronto").asBoolean()) {
                assertThat(p.get("observacoes").asText()).as("pacote não pronto precisa dizer por quê").isNotBlank();
            }
        }
        assertThat(idsDePacotes).containsExactlyInAnyOrder("pdv", "petshop", "agropecuaria", "clinica", "completo");
        for (JsonNode p : c.raiz.get("pacotes")) {
            if (p.get("id").asText().equals("completo")) {
                assertThat(CatalogoModulos.textos(p, "modulos")).containsExactlyInAnyOrderElementsOf(c.idsDosModulos());
            }
            if (p.get("id").asText().equals("agropecuaria")) {
                assertThat(p.get("pronto").asBoolean()).as("agropecuária depende de unidade/decimal (D24)").isFalse();
            }
        }
    }

    @Test
    void cadaModuloExistenteTemCapacidadeRastreadaEAcoesNaoSeRepetem() {
        Set<String> acoes = new HashSet<>();
        for (String id : c.idsDosModulos()) {
            JsonNode m = c.modulo(id);
            int capacidades = CatalogoModulos.textos(m, "rotas").size() + CatalogoModulos.textos(m, "telas").size();
            assertThat(capacidades).as("módulo " + id + " precisa de rota ou tela").isPositive();
            for (String a : CatalogoModulos.textos(m, "acoes")) {
                assertThat(acoes.add(a)).as("ação repetida em módulos diferentes: " + a).isTrue();
            }
        }
        for (String a : CatalogoModulos.textos(c.raiz.get("nucleo"), "acoes")) {
            assertThat(acoes.add(a)).as("ação do núcleo repetida: " + a).isTrue();
        }
        c.raiz.get("integracoes").forEach(i -> CatalogoModulos.textos(i, "acoes").forEach(a -> assertThat(acoes.add(a)).as(a).isTrue()));
    }

    @Test
    void bloqueiosDeRemocaoCitamModulosEOLotesNuncaSaiComProdutoControlado() {
        List<String> ids = c.idsDosModulos();
        Set<String> comBloqueio = new HashSet<>();
        for (JsonNode b : c.raiz.get("bloqueiosDeRemocao")) {
            assertThat(ids).contains(b.get("modulo").asText());
            assertThat(b.get("motivo").asText()).isNotBlank();
            assertThat(b.get("verificacao").asText()).isNotBlank();
            comBloqueio.add(b.get("modulo").asText());
            if (b.get("modulo").asText().equals("lotes")) {
                assertThat(b.get("verificacao").asText()).contains("controla_lote");
            }
        }
        // Todo módulo que guarda estado operacional pendente tem bloqueio.
        assertThat(comBloqueio).contains("lotes", "caixa", "pdv", "compras", "financeiro", "agenda", "clinica");
    }

    @Test
    void ausenciasEstaoDeclaradasExplicitamente() {
        List<String> ausencias = new ArrayList<>();
        c.raiz.get("ausencias").forEach(a -> ausencias.add(a.get("capacidade").asText()));
        assertThat(ausencias).anyMatch(a -> a.contains("multiempresa"));
        assertThat(ausencias).anyMatch(a -> a.contains("nota fiscal"));
        assertThat(ausencias).anyMatch(a -> a.contains("granel"));
        assertThat(ausencias).anyMatch(a -> a.contains("prontuário"));
        c.raiz.get("ausencias").forEach(a -> assertThat(a.get("observacao").asText()).isNotBlank());
    }
}
