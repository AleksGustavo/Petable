package br.com.tecnodreamer.petconnect.modulos;

import br.com.tecnodreamer.petconnect.IntegrationTest;
import br.com.tecnodreamer.petconnect.shared.config.RegrasDeAcesso;
import br.com.tecnodreamer.petconnect.shared.modulos.CatalogoModulos;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Matriz endpoint → módulo → papel, versionada (etapa 07). Gerada a partir das rotas REAIS, do catálogo de módulos e da mesma lista de
 * regras de URL que o Spring Security aplica ({@link RegrasDeAcesso}) mais o {@code @PreAuthorize} de cada método. Compara com
 * {@code docs/implementacao/07_MATRIZ_ACESSO.md}: qualquer mudança de rota, módulo ou papel reprova o build até a matriz ser regenerada
 * de propósito ({@code MATRIZ_ATUALIZAR=true ./mvnw verify -Dit.test=MatrizAcessoIT}) e revisada no PR.
 */
class MatrizAcessoIT extends IntegrationTest {

    private static final Path ARQUIVO = Path.of("..", "docs", "implementacao", "07_MATRIZ_ACESSO.md");
    private static final Set<String> TODOS = Set.of("ADMIN", "VENDAS", "SERVICO");
    private static final Pattern PAPEIS = Pattern.compile("'([A-Z]+)'");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mapeamento;

    private final CatalogoModulos catalogo = CatalogoModulos.carregar();

    private record Linha(String modulo, String metodo, String rota, String papeis) {
    }

    @Test
    void matrizVersionadaConfereComAApi() throws IOException {
        List<Linha> linhas = montar();
        String gerado = renderizar(linhas);

        // Invariantes independentes do arquivo.
        assertThat(linhas).as("rotas na matriz").hasSizeGreaterThan(120);
        assertThat(linhas).allSatisfy(l -> assertThat(l.papeis()).as(l.rota()).isNotBlank());
        assertThat(linhas).filteredOn(l -> l.modulo().equals("infra")).isEmpty();
        // Só as rotas de entrada (login, esqueci e redefinir senha) são públicas; toda a API de módulo exige papel ou autenticação.
        assertThat(linhas).filteredOn(l -> l.papeis().equals("pública")).extracting(Linha::rota)
                .containsExactlyInAnyOrder("/api/v1/auth/login", "/api/v1/auth/esqueci-senha", "/api/v1/auth/redefinir-senha");

        if ("true".equalsIgnoreCase(System.getenv("MATRIZ_ATUALIZAR"))) {
            Files.writeString(ARQUIVO, gerado, StandardCharsets.UTF_8);
            return;
        }
        assertThat(Files.exists(ARQUIVO)).as("falta " + ARQUIVO + ": rode com MATRIZ_ATUALIZAR=true").isTrue();
        String commitado = Files.readString(ARQUIVO, StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertThat(gerado.replace("\r\n", "\n"))
                .as("A matriz de acesso mudou. Se a mudança é intencional, regenere com MATRIZ_ATUALIZAR=true e revise o diff no PR.")
                .isEqualTo(commitado);
    }

    private List<Linha> montar() {
        List<Linha> r = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : mapeamento.getHandlerMethods().entrySet()) {
            HandlerMethod hm = e.getValue();
            if (hm.getBeanType().getPackageName().startsWith("org.")) {
                continue; // infraestrutura (erro, OpenAPI, Swagger)
            }
            PreAuthorize pa = AnnotatedElementUtils.findMergedAnnotation(hm.getMethod(), PreAuthorize.class);
            if (pa == null) {
                pa = AnnotatedElementUtils.findMergedAnnotation(hm.getBeanType(), PreAuthorize.class);
            }
            Set<String> metodos = new TreeSet<>(e.getKey().getMethodsCondition().getMethods().stream().map(Enum::name).toList());
            if (metodos.isEmpty()) {
                metodos = Set.of("GET");
            }
            for (String caminho : e.getKey().getPathPatternsCondition().getPatternValues()) {
                for (String metodo : metodos) {
                    // Exportação por tipo: cada tipo é uma linha (o módulo de origem e a regra de URL variam por tipo).
                    List<String> caminhos = caminho.contains("{tipo}")
                            ? catalogo.porRelatorio().keySet().stream().sorted().map(t -> caminho.replace("{tipo}", t)).toList() : List.of(caminho);
                    for (String c : caminhos) {
                        r.add(new Linha(modulo(metodo, c), metodo, c, papeis(metodo, c, pa == null ? null : pa.value())));
                    }
                }
            }
        }
        List<String> ordem = new ArrayList<>(List.of("nucleo"));
        ordem.addAll(catalogo.idsDosModulos());
        ordem.addAll(catalogo.modulosDasIntegracoes().keySet());
        r.sort(Comparator.<Linha>comparingInt(l -> posicao(ordem, l.modulo())).thenComparing(Linha::rota).thenComparing(Linha::metodo));
        return r;
    }

    private static int posicao(List<String> ordem, String modulo) {
        String base = modulo.contains(" ") ? modulo.substring(0, modulo.indexOf(' ')) : modulo;
        int i = ordem.indexOf(base);
        return i < 0 ? 999 : i;
    }

    private String modulo(String metodo, String caminho) {
        var r = catalogo.resolver(metodo, caminho);
        assertThat(r.donos()).as("dono de " + metodo + " " + caminho).hasSize(1);
        CatalogoModulos.Dono d = r.dono();
        String rotulo = d.tipo() == CatalogoModulos.TipoDono.INTEGRACAO
                ? d.id() + " (" + String.join("+", catalogo.modulosDasIntegracoes().get(d.id())) + ")" : d.id();
        if (d.id().equals("relatorios")) {
            String tipo = caminho.replaceFirst("^/api/v1/relatorios/", "").split("/")[0];
            var origem = catalogo.porRelatorio().get(tipo);
            if (origem != null) {
                rotulo += " (+" + String.join("+", origem) + ")";
            }
        }
        return rotulo;
    }

    /** Papel efetivo: a regra de URL e o @PreAuthorize valem os dois (interseção). */
    private String papeis(String metodo, String caminho, String preAuthorize) {
        var url = RegrasDeAcesso.regraDe(metodo, caminho);
        if (url.isPresent() && url.get() == RegrasDeAcesso.Quem.PUBLICO) {
            return "pública";
        }
        Set<String> daUrl = url.map(q -> q.papeis()).orElse(TODOS);
        Set<String> doMetodo = TODOS;
        if (preAuthorize != null && !preAuthorize.contains("isAuthenticated")) {
            Set<String> achados = new TreeSet<>();
            Matcher m = PAPEIS.matcher(preAuthorize);
            while (m.find()) {
                achados.add(m.group(1));
            }
            doMetodo = achados.isEmpty() ? TODOS : achados;
        }
        Set<String> efetivo = new TreeSet<>(daUrl);
        efetivo.retainAll(doMetodo);
        if (efetivo.equals(TODOS)) {
            return "qualquer papel";
        }
        return efetivo.isEmpty() ? "NINGUÉM" : String.join(", ", efetivo);
    }

    private String renderizar(List<Linha> linhas) {
        StringBuilder s = new StringBuilder();
        s.append("# Matriz de acesso: endpoint → módulo → papel (versionada)\n\n");
        s.append("> **Gerada pelo teste `MatrizAcessoIT`**; não edite à mão. Lê as rotas reais da API, o catálogo de módulos (`modulos/catalogo.json`), ")
                .append("a lista de regras de URL (`RegrasDeAcesso`) e o `@PreAuthorize` de cada método. Para atualizar depois de uma mudança intencional: ")
                .append("`MATRIZ_ATUALIZAR=true ./mvnw verify -Dit.test=MatrizAcessoIT` e revise o diff no PR.\n\n");
        s.append("**Como ler.** O acesso exige as DUAS camadas, nesta ordem: (1) o **módulo** está contratado na instalação (`PETCONNECT_MODULOS`), ")
                .append("senão `403` com `codigo = MODULO_INDISPONIVEL` para qualquer papel; (2) o **papel** do usuário (coluna *Papéis*: regra de URL e `@PreAuthorize` juntas). ")
                .append("`núcleo` está sempre ativo. `qualquer papel` = qualquer usuário logado. Relatórios e exportações dependem também do módulo de origem do tipo ")
                .append("(`relatorios (+pdv)`). Sem token a resposta é `401` e nada é revelado.\n\n");
        s.append("Total: **").append(linhas.size()).append(" rotas**.\n\n");
        s.append("| Módulo | Método | Rota | Papéis |\n|---|---|---|---|\n");
        for (Linha l : linhas) {
            s.append("| ").append(l.modulo()).append(" | ").append(l.metodo()).append(" | `").append(l.rota()).append("` | ").append(l.papeis()).append(" |\n");
        }
        return s.toString();
    }
}
