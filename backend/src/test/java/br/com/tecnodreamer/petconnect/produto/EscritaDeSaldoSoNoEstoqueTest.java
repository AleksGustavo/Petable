package br.com.tecnodreamer.petconnect.produto;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regra de arquitetura (lotes, item 7 da etapa 03): saldo de produto e de lote só muda dentro de
 * {@code produto.application} (EstoqueService e EstoqueLoteService). Um novo caminho de saída ou entrada que chame
 * {@code movimentar(...)} fora dali ignoraria o lote e quebraria a reconciliação, então o build falha.
 */
class EscritaDeSaldoSoNoEstoqueTest {

    private static final Pattern MOVIMENTAR = Pattern.compile("\\.movimentar\\(");
    private static final Pattern SALDO_DIRETO = Pattern.compile("\bquantidadeEstoque\s*=[^=]");

    @Test
    void soOPacoteDeEstoqueChamaMovimentarNoSaldo() throws IOException {
        Path fontes = Path.of("src", "main", "java");
        try (Stream<Path> arquivos = Files.walk(fontes)) {
            List<String> fora = arquivos.filter(f -> f.toString().endsWith(".java"))
                    .filter(f -> !f.toString().replace('\\', '/').contains("/produto/application/"))
                    .filter(f -> !f.toString().replace('\\', '/').contains("/produto/domain/"))
                    .filter(f -> conteudo(f, MOVIMENTAR) || conteudo(f, SALDO_DIRETO))
                    .map(Path::toString).toList();
            assertThat(fora).as("arquivos que alteram saldo fora de produto.application").isEmpty();
        }
    }

    private static boolean conteudo(Path arquivo, Pattern padrao) {
        try {
            return padrao.matcher(Files.readString(arquivo, StandardCharsets.UTF_8)).find();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
