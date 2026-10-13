package br.com.tecnodreamer.petconnect.relatorio.application;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvTest {

    private static String texto(byte[] b) {
        return new String(b, StandardCharsets.UTF_8);
    }

    @Test
    void deveComecarComBomEUsarPontoEVirgula() {
        byte[] b = Csv.gerar(List.of("Produto", "Valor (R$)"), List.of(List.of("Ração", new BigDecimal("10.50"))));

        assertThat(b[0]).isEqualTo((byte) 0xEF);
        assertThat(b[1]).isEqualTo((byte) 0xBB);
        assertThat(b[2]).isEqualTo((byte) 0xBF);
        assertThat(texto(b)).isEqualTo("\uFEFFProduto;Valor (R$)\r\nRação;10,50\r\n");
    }

    @Test
    void deveFormatarDecimalComVirgulaEDataBrasileira() {
        assertThat(Csv.campo(new BigDecimal("1234.05"))).isEqualTo("1234,05");
        assertThat(Csv.campo(LocalDate.of(2031, 3, 9))).isEqualTo("09/03/2031");
        assertThat(Csv.campo(LocalDateTime.of(2031, 3, 9, 8, 5))).isEqualTo("09/03/2031 08:05");
        assertThat(Csv.campo(null)).isEmpty();
        assertThat(Csv.campo(42L)).isEqualTo("42");
    }

    @Test
    void deveEscaparCamposComSeparadorOuAspas() {
        assertThat(Csv.campo("Ração; premium")).isEqualTo("\"Ração; premium\"");
        assertThat(Csv.campo("Coleira \"G\"")).isEqualTo("\"Coleira \"\"G\"\"\"");
        assertThat(Csv.campo("linha\nnova")).isEqualTo("\"linha\nnova\"");
    }
}
