package br.com.tecnodreamer.petconnect.migracao.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MigracaoRelatorioServiceTest {

    MigracaoRelatorioService service = new MigracaoRelatorioService();
    OpcoesMigracao opcoes = new OpcoesMigracao(false, 500, Path.of("build/tmp"), null, false, null);
    ResultadoMigracao r;
    String texto;

    @BeforeEach
    void setUp() {
        r = new ResultadoMigracao(12L, false, LocalDateTime.of(2026, 9, 27, 8, 0));
        ResultadoEntidade clientes = r.entidade("cliente");
        clientes.contar(Situacao.MIGRADO);
        clientes.contar(Situacao.AJUSTADO);
        clientes.contar(Situacao.IGNORADO);
        r.pendencia(new Pendencia("cliente", 18L, "CPF_INVALIDO"));
        r.pendencia(new Pendencia("cliente", 19L, "TELEFONE_DUPLICADO_SCHEMA_OBRIGATORIO"));
        r.conferencias(List.of(new Conferencia("vendas", true, "legado 276.00 / novo 276.00 em 2 venda(s)")));
        r.finalizar(ResultadoMigracao.Status.CONCLUIDA, null, LocalDateTime.of(2026, 9, 27, 8, 4, 31));
        texto = service.conteudo(r, opcoes);
    }

    @Test
    void relatorioNaoDeveConterCpf() {
        assertThat(texto).doesNotContainPattern("\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}");
    }

    @Test
    void relatorioNaoDeveConterEmail() {
        assertThat(texto).doesNotContain("@");
    }

    @Test
    void relatorioNaoDeveConterTelefone() {
        assertThat(texto).doesNotContainPattern("\\(?\\d{2}\\)?\\s?9?\\d{4}-?\\d{4}");
    }

    @Test
    void deveExibirSomenteIdLegadoNasPendencias() {
        assertThat(texto).contains("- CLIENTE legado #18 — CPF_INVALIDO")
                .contains("| cliente | 3 | 1 | 1 | 1 | 0 |")
                .contains("- vendas: PASS");
    }
}
