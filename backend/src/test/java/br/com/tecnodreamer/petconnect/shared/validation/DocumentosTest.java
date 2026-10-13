package br.com.tecnodreamer.petconnect.shared.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentosTest {

    @ParameterizedTest
    @ValueSource(strings = {"529.982.247-25", "52998224725", "111.444.777-35"})
    void cpfValido(String cpf) {
        assertThat(Documentos.cpfValido(cpf)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"529.982.247-24", "111.111.111-11", "123", "abc", ""})
    void cpfInvalido(String cpf) {
        assertThat(Documentos.cpfValido(cpf)).isFalse();
    }

    @Test
    void somenteDigitosRemoveMascaraEVazioViraNulo() {
        assertThat(Documentos.somenteDigitos("(19) 99876-5432")).isEqualTo("19998765432");
        assertThat(Documentos.somenteDigitos("  ")).isNull();
        assertThat(Documentos.somenteDigitos(null)).isNull();
    }

    @Test
    void telefoneExigeDdd() {
        assertThat(Documentos.telefoneValido("(19) 3456-7890")).isTrue();
        assertThat(Documentos.telefoneValido("(19) 99876-5432")).isTrue();
        assertThat(Documentos.telefoneValido("99876-5432")).isFalse();
    }

    @Test
    void cepTemOitoDigitos() {
        assertThat(Documentos.cepValido("13616-140")).isTrue();
        assertThat(Documentos.cepValido("1361-140")).isFalse();
    }
}
