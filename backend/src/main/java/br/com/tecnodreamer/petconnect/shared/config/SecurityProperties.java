package br.com.tecnodreamer.petconnect.shared.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties("app.security")
public record SecurityProperties(@NotNull Jwt jwt, @NotNull Cors cors) {

    public record Jwt(@NotBlank String secret, @NotBlank String issuer, @NotNull Duration ttl) {
    }

    public record Cors(List<String> allowedOrigins) {
        public Cors {
            allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        }
    }
}
