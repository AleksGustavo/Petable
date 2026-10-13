package br.com.tecnodreamer.petconnect.pet.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FotoPetControllerIT extends PetIntegrationTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

    private static MockMultipartFile foto(String nome, String tipo, byte[] conteudo) {
        return new MockMultipartFile("foto", nome, tipo, conteudo);
    }

    @Test
    void enviaEServeFotoValida() throws Exception {
        long petId = criarPet(criarCliente(unico("Tutor Foto")), "Paçoca", "Cão");
        String url = "/api/v1/pets/" + petId + "/foto";

        mvc.perform(get(url).with(comPapel("VENDAS"))).andExpect(status().isNotFound());

        mvc.perform(multipart(HttpMethod.PUT, url).file(foto("pacoca.png", "image/png", PNG)).with(comPapel("VENDAS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotoUrl").value(url));

        mvc.perform(get(url).with(comPapel("SERVICO")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG));
        mvc.perform(get("/api/v1/pets/" + petId).with(comPapel("SERVICO")))
                .andExpect(jsonPath("$.fotoUrl").value(url));

        assertThat(acoesAuditadas(petId)).contains("PET_FOTO_ATUALIZADA");
    }

    @Test
    void rejeitaFormatoInvalidoArquivoGrandeEAusente() throws Exception {
        long petId = criarPet(criarCliente(unico("Tutor Foto Invalida")), "Tobias", "Cão");
        String url = "/api/v1/pets/" + petId + "/foto";

        mvc.perform(multipart(HttpMethod.PUT, url).file(foto("a.gif", "image/gif", "GIF89a...".getBytes()))
                        .with(comPapel("VENDAS")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.foto").exists());

        byte[] grande = Arrays.copyOf(PNG, 5 * 1024 * 1024 + 1);
        mvc.perform(multipart(HttpMethod.PUT, url).file(foto("g.png", "image/png", grande)).with(comPapel("VENDAS")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.foto").value("A foto deve ter no máximo 5 MB."));

        mvc.perform(multipart(HttpMethod.PUT, url).with(comPapel("VENDAS")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.foto").exists());
    }

    @Test
    void servicoNaoTrocaFotoEPetInexistenteResponde404() throws Exception {
        long petId = criarPet(criarCliente(unico("Tutor Foto Permissao")), "Kiara", "Cão");

        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/pets/" + petId + "/foto")
                        .file(foto("k.png", "image/png", PNG)).with(comPapel("SERVICO")))
                .andExpect(status().isForbidden());
        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/pets/999999/foto")
                        .file(foto("k.png", "image/png", PNG)).with(comPapel("VENDAS")))
                .andExpect(status().isNotFound());
    }
}
