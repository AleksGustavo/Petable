package br.com.tecnodreamer.petconnect.shared.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import br.com.tecnodreamer.petconnect.auth.application.UsuarioJwtAuthenticationConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * API stateless: o front (Next.js) faz login em {@code /api/v1/auth/login},
 * recebe um JWT HS256 assinado por esta API e o envia como Bearer. A cada
 * requisição o token é conferido contra o banco (usuário ativo, versão da
 * credencial e papel atual) por {@link UsuarioJwtAuthenticationConverter}.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    public static final String ROLES_CLAIM = "roles";

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, UsuarioJwtAuthenticationConverter jwtConverter) throws Exception {
        http
                // Sem cookie de sessão: o token vai no header Authorization, então CSRF não se aplica.
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/esqueci-senha",
                                "/api/v1/auth/redefinir-senha").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // Métricas: em produção o management fica na porta 9090, que não é publicada (DA-12).
                        .requestMatchers("/actuator/prometheus").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/error").permitAll()
                        // Rotas administrativas barradas já no filtro: sem papel, nem a validação
                        // do corpo roda (o @PreAuthorize do método só atua depois do @Valid).
                        .requestMatchers("/api/v1/usuarios/**").hasRole("ADMIN")
                        // Etapa 09 (LGPD): exportar/anonimizar titular só ADMIN.
                        .requestMatchers("/api/v1/clientes/*/dados-pessoais", "/api/v1/clientes/*/anonimizar").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/funcionarios/**", "/api/v1/servicos/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/funcionarios/**", "/api/v1/servicos/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/servicos/**").hasRole("ADMIN")
                        // Etapa 04: fornecedores e compras (SERVICO não acessa; escrita só ADMIN).
                        .requestMatchers(HttpMethod.GET, "/api/v1/fornecedores/**", "/api/v1/compras/**").hasAnyRole("ADMIN", "VENDAS")
                        .requestMatchers("/api/v1/fornecedores/**", "/api/v1/compras/**").hasRole("ADMIN")
                        // Produtos: ajuste de estoque e reativação só ADMIN; demais escritas ADMIN/VENDAS.
                        .requestMatchers(HttpMethod.POST, "/api/v1/produtos/*/ajustes-estoque").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/produtos/*/ativar").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/produtos/**").hasAnyRole("ADMIN", "VENDAS")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/produtos/**").hasAnyRole("ADMIN", "VENDAS")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/produtos/**").hasAnyRole("ADMIN", "VENDAS")
                        // Etapa 05: vendas só ADMIN/VENDAS (SERVICO não acessa); cancelamento só ADMIN.
                        .requestMatchers(HttpMethod.POST, "/api/v1/vendas/*/cancelar").hasRole("ADMIN")
                        .requestMatchers("/api/v1/vendas/**").hasAnyRole("ADMIN", "VENDAS")
                        // Etapa 06: financeiro geral só ADMIN; listar todos os caixas só ADMIN; o resto do caixa é do próprio usuário.
                        .requestMatchers("/api/v1/financeiro/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/caixas").hasRole("ADMIN")
                        .requestMatchers("/api/v1/caixas/**").hasAnyRole("ADMIN", "VENDAS", "SERVICO")
                        // Etapa 07: relatórios de vendas/produtos/ABC para ADMIN e VENDAS; os demais só ADMIN; SERVICO nada.
                        .requestMatchers(HttpMethod.GET, "/api/v1/relatorios/vendas/**", "/api/v1/relatorios/produtos-mais-vendidos/**",
                                "/api/v1/relatorios/curva-abc/**").hasAnyRole("ADMIN", "VENDAS")
                        .requestMatchers("/api/v1/relatorios/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)));
        return http.build();
    }

    @Bean
    SecretKey jwtSigningKey(SecurityProperties props) {
        byte[] secret = props.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("app.security.jwt.secret (JWT_SECRET) precisa ter pelo menos 32 bytes");
        }
        return new SecretKeySpec(secret, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey, SecurityProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(props.jwt().issuer()));
        return decoder;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(SecurityProperties props) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(props.cors().allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
