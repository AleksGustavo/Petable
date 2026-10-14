package br.com.tecnodreamer.petconnect.modulos;

import br.com.tecnodreamer.petconnect.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rastreabilidade do catálogo ao código real (etapa 06): TODA rota da API e TODA tabela do banco tem exatamente um dono no
 * catálogo. Rota ou tabela nova sem dono reprova o build: ninguém "esquece" de dizer a qual módulo uma capacidade pertence.
 */
class CatalogoModulosRotasIT extends IntegrationTest {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mapeamento;
    @Autowired
    JdbcTemplate jdbc;

    private final CatalogoModulos catalogo = new CatalogoModulos();

    @Test
    void todaRotaDaApiTemExatamenteUmDonoNoCatalogo() {
        // Usa o MESMO resolvedor que o filtro de módulos usa em produção (shared.modulos.CatalogoModulos).
        var producao = br.com.tecnodreamer.petconnect.shared.modulos.CatalogoModulos.carregar();
        List<String> semDono = new ArrayList<>();
        List<String> ambiguas = new ArrayList<>();
        int total = 0;
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : mapeamento.getHandlerMethods().entrySet()) {
            Set<String> metodos = e.getKey().getMethodsCondition().getMethods().stream().map(Enum::name).collect(Collectors.toSet());
            for (String caminho : e.getKey().getPathPatternsCondition().getPatternValues()) {
                for (String metodo : metodos.isEmpty() ? Set.of("GET") : metodos) {
                    total++;
                    var r = producao.resolver(metodo, caminho);
                    String rotulo = metodo + " " + caminho + " (" + e.getValue().getBeanType().getSimpleName() + ")";
                    if (r.semDono()) {
                        semDono.add(rotulo);
                    } else if (r.donos().size() > 1) {
                        ambiguas.add(rotulo + " -> " + r.donos());
                    }
                }
            }
        }
        assertThat(total).as("rotas encontradas").isGreaterThan(100);
        assertThat(semDono).as("rotas SEM dono no catálogo (adicione ao módulo certo em modulos/catalogo.json)").isEmpty();
        assertThat(ambiguas).as("rotas com mais de um dono de mesma especificidade").isEmpty();
    }

    @Test
    void todaEntradaDeRotaDoCatalogoCorrespondeAUmaRotaReal() {
        // O inverso: o catálogo não promete rota que não existe ("não vender como pronto o que não está implementado").
        List<String> reais = new ArrayList<>();
        List<Set<String>> metodosReais = new ArrayList<>();
        mapeamento.getHandlerMethods().keySet().forEach(info -> {
            Set<String> metodos = info.getMethodsCondition().getMethods().stream().map(Enum::name).collect(Collectors.toSet());
            info.getPathPatternsCondition().getPatternValues().forEach(p -> {
                reais.add(p);
                metodosReais.add(metodos);
            });
        });
        var matcher = new org.springframework.util.AntPathMatcher();
        List<String> orfas = new ArrayList<>();
        for (CatalogoModulos.Entrada e : catalogo.entradasDeRotas()) {
            boolean alguma = false;
            for (int i = 0; i < reais.size() && !alguma; i++) {
                alguma = e.aceita(metodosReais.get(i), reais.get(i), matcher);
            }
            if (!alguma) {
                orfas.add(e.dono() + " " + e.metodos() + " " + e.padrao());
            }
        }
        assertThat(orfas).as("entradas do catálogo sem nenhuma rota real").isEmpty();
    }

    @Test
    void todaTabelaDoBancoTemDonoENenhumaTabelaDoCatalogoEFantasma() {
        Set<String> donas = new TreeSet<>();
        List<String> repetidas = new ArrayList<>();
        adicionarTabelas(donas, repetidas, catalogo.raiz.get("nucleo"));
        adicionarTabelas(donas, repetidas, catalogo.raiz.get("infra"));
        catalogo.raiz.get("modulos").forEach(m -> adicionarTabelas(donas, repetidas, m));
        assertThat(repetidas).as("tabela com dois donos").isEmpty();

        Set<String> reais = new TreeSet<>(jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = database() and table_type = 'BASE TABLE'", String.class));
        reais.remove("flyway_schema_history");
        assertThat(reais).as("tabelas do banco SEM dono no catálogo").isSubsetOf(donas);
        assertThat(donas).as("tabelas do catálogo que não existem no banco").isSubsetOf(reais);
    }

    private static void adicionarTabelas(Set<String> donas, List<String> repetidas, JsonNode no) {
        for (String t : CatalogoModulos.textos(no, "tabelas")) {
            if (!donas.add(t)) {
                repetidas.add(t);
            }
        }
    }
}
