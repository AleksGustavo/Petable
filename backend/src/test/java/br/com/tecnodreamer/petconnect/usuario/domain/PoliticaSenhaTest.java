package br.com.tecnodreamer.petconnect.usuario.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PoliticaSenhaTest {

    private static String violacao(String senha) {
        return PoliticaSenha.violacao(senha, "maria.silva", "maria@empresa.com.br").orElse(null);
    }

    @Test
    void deveAceitarSenhaValida() {
        assertThat(violacao("Pet@2026Seguro")).isNull();
    }

    @Test
    void deveRejeitarSenhaComMenosDeDezCaracteres() {
        assertThat(violacao("Pet@2026S")).contains("pelo menos 10");
    }

    @Test
    void deveRejeitarSenhaSemMaiuscula() {
        assertThat(violacao("pet@2026seguro")).contains("maiúscula");
    }

    @Test
    void deveRejeitarSenhaSemMinuscula() {
        assertThat(violacao("PET@2026SEGURO")).contains("minúscula");
    }

    @Test
    void deveRejeitarSenhaSemNumero() {
        assertThat(violacao("Pet@Seguro!!x")).contains("número");
    }

    @Test
    void deveRejeitarSenhaSemEspecial() {
        assertThat(violacao("Pet2026Seguro")).contains("especial");
    }

    @Test
    void deveRejeitarSenhaIgualAoUsername() {
        assertThat(PoliticaSenha.violacao("Maria.Silva1", "maria.silva1", "x@y.com")).get().asString().contains("usuário");
    }

    @Test
    void deveRejeitarSenhaIgualAoEmail() {
        assertThat(PoliticaSenha.violacao("Maria.2026@Empresa.com", "ms", "maria.2026@empresa.com")).get().asString().contains("e-mail");
    }

    @Test
    void deveRejeitarSenhaIgualAParteLocalDoEmail() {
        assertThat(PoliticaSenha.violacao("Maria.2026!", "ms", "maria.2026!@empresa.com")).get().asString().contains("e-mail");
    }

    @Test
    void deveRejeitarSenhaMaiorQueSetentaEDoisCaracteres() {
        assertThat(violacao("Aa1!" + "x".repeat(69))).contains("no máximo 72");
        assertThat(violacao("Aa1!" + "x".repeat(68))).isNull();
    }
}
