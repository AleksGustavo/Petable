package br.com.tecnodreamer.petconnect.pet.application;

import br.com.tecnodreamer.petconnect.pet.domain.Pet;
import br.com.tecnodreamer.petconnect.pet.infrastructure.LocalFotoPetStorage;
import br.com.tecnodreamer.petconnect.pet.infrastructure.PetRepository;
import br.com.tecnodreamer.petconnect.pet.infrastructure.StorageProperties;
import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

import static br.com.tecnodreamer.petconnect.pet.PetFixtures.cliente;
import static br.com.tecnodreamer.petconnect.pet.PetFixtures.especie;
import static br.com.tecnodreamer.petconnect.pet.PetFixtures.pet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Usa o storage real em diretório temporário: o que importa aqui é o arquivo gravado. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FotoPetServiceTest {

    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P'};

    @Mock PetRepository pets;
    @Mock AuditoriaService auditoria;
    @TempDir Path dir;

    FotoPetService service;
    Pet rex;

    @BeforeEach
    void setUp() {
        service = new FotoPetService(pets, new LocalFotoPetStorage(new StorageProperties(dir)), auditoria);
        rex = pet(1L, cliente(7L), especie(1L, "Cão"), null, null);
        when(pets.findById(1L)).thenReturn(Optional.of(rex));
    }

    private static MockMultipartFile arquivo(String nome, String contentType, byte[] conteudo) {
        return new MockMultipartFile("foto", nome, contentType, conteudo);
    }

    private void deveAceitar(byte[] conteudo, String extensao, String contentType) throws Exception {
        service.atualizar(1L, arquivo("foto." + extensao, contentType, conteudo));

        assertThat(rex.getFotoUrl()).endsWith("." + extensao);
        assertThat(Files.readAllBytes(dir.resolve(rex.getFotoUrl()))).isEqualTo(conteudo);
        assertThat(service.carregar(1L).contentType()).isEqualTo(contentType);
        verify(auditoria).registrar(eq("PET_FOTO_ATUALIZADA"), eq("pet"), eq(1L), anyMap());
    }

    @Test
    void deveAceitarJpeg() throws Exception {
        deveAceitar(JPEG, "jpg", "image/jpeg");
    }

    @Test
    void deveAceitarPng() throws Exception {
        deveAceitar(PNG, "png", "image/png");
    }

    @Test
    void deveAceitarWebp() throws Exception {
        deveAceitar(WEBP, "webp", "image/webp");
    }

    @Test
    void deveRejeitarTipoNaoPermitido() {
        // Content-Type e extensão mentindo: o conteúdo é GIF, depois SVG.
        assertThatThrownBy(() -> service.atualizar(1L, arquivo("a.png", "image/png", "GIF89a....".getBytes())))
                .isInstanceOf(CampoInvalidoException.class);
        assertThatThrownBy(() -> service.atualizar(1L,
                arquivo("a.jpg", "image/jpeg", "<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes())))
                .isInstanceOf(CampoInvalidoException.class);
        assertThat(rex.getFotoUrl()).isNull();
    }

    @Test
    void deveRejeitarArquivoMaiorQueCincoMegabytes() {
        byte[] grande = Arrays.copyOf(JPEG, (int) FotoPetService.TAMANHO_MAXIMO + 1);

        assertThatThrownBy(() -> service.atualizar(1L, arquivo("grande.jpg", "image/jpeg", grande)))
                .isInstanceOf(CampoInvalidoException.class)
                .hasMessageContaining("5 MB");
    }

    @Test
    void deveGerarNomeInternoSemUsarNomeOriginal() {
        service.atualizar(1L, arquivo("../../etc/minha-foto-do-rex.jpg", "image/jpeg", JPEG));

        assertThat(rex.getFotoUrl())
                .doesNotContain("minha-foto")
                .doesNotContain("/")
                .matches("[0-9a-f-]{36}\\.jpg");
    }

    @Test
    void deveSubstituirFotoExistente() {
        service.atualizar(1L, arquivo("a.jpg", "image/jpeg", JPEG));
        String antiga = rex.getFotoUrl();

        service.atualizar(1L, arquivo("b.png", "image/png", PNG));

        assertThat(rex.getFotoUrl()).isNotEqualTo(antiga).endsWith(".png");
        assertThat(dir.resolve(antiga)).doesNotExist();
        assertThat(dir.resolve(rex.getFotoUrl())).exists();
    }
}
