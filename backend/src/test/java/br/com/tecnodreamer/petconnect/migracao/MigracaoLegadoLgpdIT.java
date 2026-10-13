package br.com.tecnodreamer.petconnect.migracao;

import br.com.tecnodreamer.petconnect.migracao.application.ResultadoEntidade;
import br.com.tecnodreamer.petconnect.migracao.application.ResultadoMigracao;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.tecnodreamer.petconnect.migracao.application.OpcoesMigracao;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

@ExtendWith(OutputCaptureExtension.class)
class MigracaoLegadoLgpdIT extends MigracaoIntegrationTest {

    private static List<String> dadosDaFixture() throws Exception {
        return new String(new ClassPathResource("legacy/documentos-fixture.txt").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8).lines().filter(l -> !l.isBlank() && !l.startsWith("#")).toList();
    }

    private static void semDadosPessoais(String texto) throws Exception {
        for (String dado : dadosDaFixture()) {
            assertThat(texto).doesNotContain(dado).doesNotContain(dado.replaceAll("\\D", "").length() >= 11
                    ? dado.replaceAll("\\D", "") : dado);
        }
    }

    @Test
    void relatorioNaoDeveConterDadosPessoaisDaFixture() throws Exception {
        for (ResultadoMigracao r : List.of(cenario.dryRun(), cenario.falha(), cenario.real(), cenario.repeticao())) {
            semDadosPessoais(Files.readString(r.relatorio()));
        }
        semDadosPessoais(String.join("\n", jdbc.queryForList(
                "SELECT COALESCE(motivo, '') FROM migracao_legado_mapa", String.class)));
        semDadosPessoais(String.join("\n", jdbc.queryForList(
                "SELECT COALESCE(mensagem, '') FROM migracao_legado_execucao", String.class)));
    }

    @Test
    void logsNaoDevemConterSegredosOuDocumentos(CapturedOutput saida) throws Exception {
        OpcoesMigracao dry = new OpcoesMigracao(true, 500, diretorio("relatorios"), null, false, null);
        ResultadoMigracao r = migracao.executar(dry, legado());

        assertThat(saida.getAll()).contains("Migração #" + r.execucaoId());
        semDadosPessoais(saida.getAll());
        assertThat(saida.getAll()).doesNotContain("Leitura-Legado-2026");
    }
}
