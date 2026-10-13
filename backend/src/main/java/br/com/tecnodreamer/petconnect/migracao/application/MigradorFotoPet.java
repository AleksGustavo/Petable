package br.com.tecnodreamer.petconnect.migracao.application;

import br.com.tecnodreamer.petconnect.pet.application.FotoPetService;
import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Fotos (DA-31, DA-32). O caminho do legado é resolvido DENTRO de
 * petconnect.migracao.fotos-dir (sem path traversal nem link simbólico para fora) e o
 * arquivo passa pela validação do {@link FotoPetService}. Falha de foto nunca aborta.
 */
@Component
@Order(130)
public class MigradorFotoPet implements Migrador {

    private static final long TAMANHO_MAXIMO = 5L * 1024 * 1024;

    private final FotoPetService fotos;

    public MigradorFotoPet(FotoPetService fotos) {
        this.fotos = fotos;
    }

    @Override
    public String entidade() {
        return "pet_foto";
    }

    @Override
    public String consulta() {
        return "SELECT id, foto FROM pet WHERE foto IS NOT NULL AND foto <> '' AND id > :ultimo ORDER BY id LIMIT :lote";
    }

    @Override
    public Desfecho migrar(Linha l, Contexto ctx) {
        Optional<Long> pet = ctx.idNovo("pet", l.id());
        if (pet.isEmpty()) {
            return Desfecho.ignorado("PET_NAO_MIGRADO");
        }
        Path dir = ctx.opcoes().fotosDir();
        if (dir == null) {
            return Desfecho.ignorado("FOTOS_DIR_NAO_CONFIGURADO");
        }
        Path raiz;
        try {
            raiz = dir.toRealPath();
        } catch (IOException e) {
            return Desfecho.ignorado("FOTOS_DIR_INEXISTENTE");
        }
        Optional<Path> arquivo;
        try {
            arquivo = resolver(raiz, l.texto("foto"));
        } catch (InvalidPathException e) {
            return Desfecho.ignorado("FOTO_CAMINHO_INVALIDO");
        }
        if (arquivo == null) {
            return Desfecho.ignorado("FOTO_CAMINHO_INVALIDO");
        }
        if (arquivo.isEmpty()) {
            return Desfecho.ignorado("FOTO_AUSENTE");
        }
        try {
            if (Files.size(arquivo.get()) > TAMANHO_MAXIMO) {
                return Desfecho.ignorado("FOTO_INVALIDA");
            }
            fotos.importar(pet.get(), Files.readAllBytes(arquivo.get()));
        } catch (IOException e) {
            return Desfecho.ignorado("FOTO_ILEGIVEL");
        } catch (CampoInvalidoException e) {
            return Desfecho.ignorado("FOTO_INVALIDA");
        }
        return Desfecho.migrado(pet.get());
    }

    /**
     * {@code null} = caminho aponta para fora da raiz (tentativa de traversal);
     * vazio = arquivo não existe. Tenta o caminho relativo e, depois, só o nome do arquivo.
     */
    static Optional<Path> resolver(Path raiz, String foto) {
        Path relativo = Path.of(foto.replace('\\', '/'));
        Path candidato = raiz.resolve(relativo).normalize();
        if (relativo.isAbsolute() || !candidato.startsWith(raiz)) {
            return null;
        }
        Path soNome = relativo.getFileName() == null ? null : raiz.resolve(relativo.getFileName()).normalize();
        for (Path p : new Path[] {candidato, soNome}) {
            if (p != null && p.startsWith(raiz) && Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }
}
