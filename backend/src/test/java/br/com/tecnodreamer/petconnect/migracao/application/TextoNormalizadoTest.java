package br.com.tecnodreamer.petconnect.migracao.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextoNormalizadoTest {

    @Test
    void deveRemoverAcentosParaComparacao() {
        assertThat(TextoNormalizado.comparacao("Siamês")).isEqualTo("siames");
        assertThat(TextoNormalizado.iguais("Ração", "racao")).isTrue();
    }

    @Test
    void deveIgnorarMaiusculasEMinusculas() {
        assertThat(TextoNormalizado.iguais("GATO", "Gato")).isTrue();
        assertThat(TextoNormalizado.enumeracao("Nao_Compareceu")).isEqualTo("nao compareceu");
    }

    @Test
    void deveNormalizarEspacos() {
        assertThat(TextoNormalizado.comparacao("  Banho   e  Tosa ")).isEqualTo("banho e tosa");
        assertThat(TextoNormalizado.comparacao("   ")).isNull();
    }

    @Test
    void deveTratarSinonimoDeEspecie() {
        assertThat(TextoNormalizado.especie("Cachorro")).isEqualTo(TextoNormalizado.especie("Cão"));
    }
}
