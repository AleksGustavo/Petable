package br.com.tecnodreamer.petconnect.pet.application;

import br.com.tecnodreamer.petconnect.pet.domain.Pet;
import br.com.tecnodreamer.petconnect.pet.domain.TipoImagem;
import br.com.tecnodreamer.petconnect.pet.infrastructure.FotoPetStorage;
import br.com.tecnodreamer.petconnect.pet.infrastructure.PetRepository;
import br.com.tecnodreamer.petconnect.pet.web.FotoResponse;
import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;
import br.com.tecnodreamer.petconnect.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

@Service
public class FotoPetService {

    static final long TAMANHO_MAXIMO = 5L * 1024 * 1024;

    private final PetRepository pets;
    private final FotoPetStorage storage;
    private final AuditoriaService auditoria;

    public FotoPetService(PetRepository pets, FotoPetStorage storage, AuditoriaService auditoria) {
        this.pets = pets;
        this.storage = storage;
        this.auditoria = auditoria;
    }

    public record Foto(byte[] conteudo, String contentType) {
    }

    @Transactional
    public FotoResponse atualizar(Long petId, MultipartFile arquivo) {
        Pet pet = petAtivo(petId);
        if (arquivo == null || arquivo.isEmpty()) {
            throw new CampoInvalidoException("foto", "Selecione uma imagem.");
        }
        if (arquivo.getSize() > TAMANHO_MAXIMO) {
            throw new CampoInvalidoException("foto", "A foto deve ter no máximo 5 MB.");
        }
        gravar(pet, bytes(arquivo));
        return new FotoResponse("/api/v1/pets/" + petId + "/foto");
    }

    /**
     * Migração do legado: mesmas regras do upload (5 MB, JPEG/PNG/WebP detectado pelo
     * conteúdo), mas vale também para pet inativo. Se a transação for desfeita (dry-run),
     * o arquivo gravado é apagado.
     */
    @Transactional
    public void importar(Long petId, byte[] conteudo) {
        Pet pet = pets.findById(petId).orElseThrow(() -> new NotFoundException("Pet", petId));
        if (conteudo == null || conteudo.length == 0) {
            throw new CampoInvalidoException("foto", "Arquivo vazio.");
        }
        if (conteudo.length > TAMANHO_MAXIMO) {
            throw new CampoInvalidoException("foto", "A foto deve ter no máximo 5 MB.");
        }
        String chave = gravar(pet, conteudo);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        storage.remover(chave);
                    }
                }
            });
        }
    }

    private String gravar(Pet pet, byte[] conteudo) {
        TipoImagem tipo = TipoImagem.detectar(conteudo).orElseThrow(() ->
                new CampoInvalidoException("foto", "Formato não permitido: envie uma imagem JPEG, PNG ou WebP."));
        String chave = storage.salvar(conteudo, tipo);
        String anterior = pet.definirFoto(chave);
        pets.save(pet);
        if (anterior != null) {
            removerDepoisDoCommit(anterior);
        }
        auditoria.registrar("PET_FOTO_ATUALIZADA", PetService.ENTIDADE, pet.getId(),
                Map.of("tipo", tipo.getContentType(), "bytes", conteudo.length));
        return chave;
    }

    @Transactional(readOnly = true)
    public Foto carregar(Long petId) {
        Pet pet = petAtivo(petId);
        String chave = pet.getFotoUrl();
        TipoImagem tipo = TipoImagem.porChave(chave).orElseThrow(() -> new NotFoundException("Foto do pet", petId));
        return new Foto(storage.ler(chave), tipo.getContentType());
    }

    /** O arquivo antigo só some se a troca foi confirmada no banco. */
    private void removerDepoisDoCommit(String chave) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    storage.remover(chave);
                }
            });
        } else {
            storage.remover(chave);
        }
    }

    private Pet petAtivo(Long petId) {
        return pets.findById(petId).filter(Pet::isAtivo).orElseThrow(() -> new NotFoundException("Pet", petId));
    }

    private static byte[] bytes(MultipartFile arquivo) {
        try {
            return arquivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
