package br.com.tecnodreamer.petconnect.pet.infrastructure;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;

/** {@code petconnect.storage.pets}: diretório das fotos (volume no Docker, temporário nos testes). */
@Validated
@ConfigurationProperties("petconnect.storage")
public record StorageProperties(@NotNull Path pets) {
}
