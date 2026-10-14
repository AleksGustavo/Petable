package br.com.tecnodreamer.petconnect.shared.config;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Regras de URL por papel da API, num só lugar (política central, etapa 07). {@link SecurityConfig} aplica esta lista na ordem; o teste
 * da matriz de acesso lê a MESMA lista para calcular o papel efetivo de cada rota (regra de URL e {@code @PreAuthorize}), então a matriz
 * versionada em {@code docs/implementacao/07_MATRIZ_ACESSO.md} nunca diverge do que a API faz. A primeira regra que combina vale.
 *
 * <p>O acesso por MÓDULO é outra camada, anterior a esta ({@code ModulosFilter}); o acesso exige as duas.
 */
public final class RegrasDeAcesso {

    public enum Quem {
        PUBLICO, ADMIN, ADMIN_VENDAS, TODOS_OS_PAPEIS;

        public Set<String> papeis() {
            return switch (this) {
                case PUBLICO -> Set.of();
                case ADMIN -> Set.of("ADMIN");
                case ADMIN_VENDAS -> Set.of("ADMIN", "VENDAS");
                case TODOS_OS_PAPEIS -> Set.of("ADMIN", "VENDAS", "SERVICO");
            };
        }
    }

    /** {@code metodos} vazio = qualquer método. */
    public record Regra(Set<HttpMethod> metodos, List<String> padroes, Quem quem) {
    }

    private static Regra r(Quem quem, String... padroes) {
        return new Regra(Set.of(), List.of(padroes), quem);
    }

    private static Regra r(Quem quem, Set<HttpMethod> metodos, String... padroes) {
        return new Regra(metodos, List.of(padroes), quem);
    }

    public static final List<Regra> REGRAS = List.of(
            r(Quem.PUBLICO, Set.of(HttpMethod.POST), "/api/v1/auth/login", "/api/v1/auth/esqueci-senha", "/api/v1/auth/redefinir-senha"),
            // Rotas administrativas barradas já no filtro: sem papel, nem a validação do corpo roda.
            r(Quem.ADMIN, "/api/v1/usuarios/**"),
            // Etapa 09 (LGPD): exportar/anonimizar titular só ADMIN.
            r(Quem.ADMIN, "/api/v1/clientes/*/dados-pessoais", "/api/v1/clientes/*/anonimizar"),
            r(Quem.ADMIN, Set.of(HttpMethod.POST, HttpMethod.PUT), "/api/v1/funcionarios/**", "/api/v1/servicos/**"),
            r(Quem.ADMIN, Set.of(HttpMethod.DELETE), "/api/v1/servicos/**"),
            // Etapa 04: fornecedores e compras (SERVICO não acessa; escrita só ADMIN).
            r(Quem.ADMIN_VENDAS, Set.of(HttpMethod.GET), "/api/v1/fornecedores/**", "/api/v1/compras/**"),
            r(Quem.ADMIN, "/api/v1/fornecedores/**", "/api/v1/compras/**"),
            // Produtos: ajuste de estoque e reativação só ADMIN; demais escritas ADMIN/VENDAS.
            r(Quem.ADMIN, Set.of(HttpMethod.POST), "/api/v1/produtos/*/ajustes-estoque"),
            r(Quem.ADMIN, Set.of(HttpMethod.PATCH), "/api/v1/produtos/*/ativar"),
            r(Quem.ADMIN_VENDAS, Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH), "/api/v1/produtos/**"),
            // Etapa 05: vendas só ADMIN/VENDAS (SERVICO não acessa); cancelamento só ADMIN.
            r(Quem.ADMIN, Set.of(HttpMethod.POST), "/api/v1/vendas/*/cancelar"),
            r(Quem.ADMIN_VENDAS, "/api/v1/vendas/**"),
            // Etapa 06: financeiro geral só ADMIN; listar todos os caixas só ADMIN; o resto do caixa é do próprio usuário.
            r(Quem.ADMIN, "/api/v1/financeiro/**"),
            r(Quem.ADMIN, Set.of(HttpMethod.GET), "/api/v1/caixas"),
            r(Quem.TODOS_OS_PAPEIS, "/api/v1/caixas/**"),
            // Etapa 07: relatórios de vendas/produtos/ABC para ADMIN e VENDAS; os demais só ADMIN; SERVICO nada.
            r(Quem.ADMIN_VENDAS, Set.of(HttpMethod.GET), "/api/v1/relatorios/vendas/**", "/api/v1/relatorios/produtos-mais-vendidos/**",
                    "/api/v1/relatorios/curva-abc/**"),
            r(Quem.ADMIN, "/api/v1/relatorios/**"));

    private RegrasDeAcesso() {
    }

    /** Aplica as regras, na ordem, ao registro de autorização do Spring Security. */
    public static void aplicar(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        for (Regra regra : REGRAS) {
            String[] padroes = regra.padroes().toArray(String[]::new);
            if (regra.metodos().isEmpty()) {
                aplicarQuem(auth.requestMatchers(padroes), regra.quem());
            } else {
                for (HttpMethod metodo : regra.metodos()) {
                    aplicarQuem(auth.requestMatchers(metodo, padroes), regra.quem());
                }
            }
        }
    }

    private static void aplicarQuem(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizedUrl url, Quem quem) {
        switch (quem) {
            case PUBLICO -> url.permitAll();
            case ADMIN -> url.hasRole("ADMIN");
            case ADMIN_VENDAS -> url.hasAnyRole("ADMIN", "VENDAS");
            case TODOS_OS_PAPEIS -> url.hasAnyRole("ADMIN", "VENDAS", "SERVICO");
        }
    }

    /** Papéis que a regra de URL deixa passar para o método e o caminho (modelo da rota, com {@code {id}}); vazio = só autenticado. */
    public static Optional<Quem> regraDe(String metodo, String caminho) {
        AntPathMatcher m = new AntPathMatcher();
        for (Regra regra : REGRAS) {
            boolean metodoOk = regra.metodos().isEmpty() || regra.metodos().stream().anyMatch(x -> x.name().equals(metodo));
            if (metodoOk && regra.padroes().stream().anyMatch(p -> m.match(p, caminho))) {
                return Optional.of(regra.quem());
            }
        }
        return Optional.empty();
    }
}
