package br.com.tecnodreamer.petconnect.shared.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MascaramentoLogsTest {

    @Test
    void deveMascararCpfComESemMascara() {
        assertThat(MascaraDadosPessoais.mascarar("cpf 529.982.247-25 duplicado")).isEqualTo("cpf ***.***.***-** duplicado");
        assertThat(MascaraDadosPessoais.mascarar("Duplicate entry '52998224725' for key"))
                .doesNotContain("52998224725");
    }

    @Test
    void deveMascararEmailMantendoDominio() {
        assertThat(MascaraDadosPessoais.mascarar("enviado para maria.silva@exemplo.com.br"))
                .isEqualTo("enviado para ***@exemplo.com.br");
    }

    @Test
    void deveMascararTelefone() {
        assertThat(MascaraDadosPessoais.mascarar("tel (11) 98888-7777")).doesNotContain("98888").doesNotContain("7777");
        assertThat(MascaraDadosPessoais.mascarar("tel 1133334444")).doesNotContain("33334444");
    }

    @Test
    void deveMascararJwtEBearer() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9.abcDEF123_-";
        assertThat(MascaraDadosPessoais.mascarar("token=" + jwt)).isEqualTo("token=***jwt***");
        assertThat(MascaraDadosPessoais.mascarar("Authorization: Bearer abc.def-123")).isEqualTo("Authorization: Bearer ***");
    }

    @Test
    void naoDeveAlterarTextoNeutro() {
        String s = "Migração #12 CONCLUIDA: lidos=66, venda #3 em 2026-09-27T08:23:04";
        assertThat(MascaraDadosPessoais.mascarar(s)).isEqualTo(s);
        assertThat(MascaraDadosPessoais.mascarar(null)).isNull();
    }

    @Test
    void customizerDeveSerInstanciavelPeloBoot() throws Exception {
        assertThat(MascaramentoLogs.class.getDeclaredConstructor().newInstance()).isNotNull();
    }
}
