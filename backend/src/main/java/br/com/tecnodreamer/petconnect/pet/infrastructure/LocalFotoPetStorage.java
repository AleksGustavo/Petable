package br.com.tecnodreamer.petconnect.pet.infrastructure;

import br.com.tecnodreamer.petconnect.pet.domain.TipoImagem;
import br.com.tecnodreamer.petconnect.shared.error.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@EnableConfigurationProperties(StorageProperties.class)
public class LocalFotoPetStorage implements FotoPetStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFotoPetStorage.class);

    /** Só chaves geradas por nós (UUID + extensão permitida): bloqueia path traversal. */
    private static final Pattern CHAVE = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)");

    private final Path base;

    public LocalFotoPetStorage(StorageProperties props) {
        this.base = props.pets().toAbsolutePath().normalize();
        try {
            Files.createDirectories(base);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível criar o diretório de fotos " + base, e);
        }
    }

    @Override
    public String salvar(byte[] conteudo, TipoImagem tipo) {
        String chave = UUID.randomUUID() + "." + tipo.getExtensao();
        Path destino = resolver(chave);
        try {
            Path temp = Files.createTempFile(base, "upload-", ".tmp");
            Files.write(temp, conteudo);
            Files.move(temp, destino, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gravar foto", e);
        }
        return chave;
    }

    @Override
    public byte[] ler(String chave) {
        try {
            return Files.readAllBytes(resolver(chave));
        } catch (NoSuchFileException e) {
            throw new NotFoundException("Foto", "solicitada");
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler foto", e);
        }
    }

    @Override
    public void remover(String chave) {
        try {
            Files.deleteIfExists(resolver(chave));
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Não foi possível remover a foto {}: {}", chave, e.getMessage());
        }
    }

    private Path resolver(String chave) {
        if (chave == null || !CHAVE.matcher(chave).matches()) {
            throw new IllegalArgumentException("Chave de foto inválida");
        }
        Path caminho = base.resolve(chave).normalize();
        if (!caminho.startsWith(base)) {
            throw new IllegalArgumentException("Chave de foto inválida");
        }
        return caminho;
    }
}
