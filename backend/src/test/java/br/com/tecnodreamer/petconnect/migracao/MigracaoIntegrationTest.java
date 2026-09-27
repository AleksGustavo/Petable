package br.com.tecnodreamer.petconnect.migracao;

import br.com.tecnodreamer.petconnect.migracao.application.MigracaoLegadoService;
import br.com.tecnodreamer.petconnect.migracao.application.OpcoesMigracao;
import br.com.tecnodreamer.petconnect.migracao.application.ResultadoMigracao;
import br.com.tecnodreamer.petconnect.pet.web.PetIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.mysql.MySQLContainer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base dos ITs da migração. O banco legado {@code petshop_db} é criado no MESMO container
 * MySQL (esquema + fixture sintética) e lido por um usuário só com SELECT.
 * <p>
 * O mapa de migração vive no banco novo compartilhado por todos os ITs, então o cenário
 * roda UMA vez por JVM, na ordem: dry-run → real que falha (SELECT revogado em venda) →
 * real que retoma → real repetida. Cada classe verifica uma parte desse cenário.
 */
public abstract class MigracaoIntegrationTest extends PetIntegrationTest {

    protected static final String BANCO_LEGADO = "petshop_db";
    protected static final String USUARIO_LEITURA = "legado_read";
    private static final String SENHA_LEITURA = "Leitura-Legado-2026";
    protected static final List<String> TABELAS_NEGOCIO = List.of("usuario", "endereco", "cliente", "funcionario",
            "fornecedor", "especie", "raca", "pet", "vacina", "carteira_vacina", "historico_consulta", "servico",
            "preco_servico_porte", "agendamento", "servico_realizado", "categoria_produto", "produto", "compra",
            "item_compra", "venda", "item_venda", "movimentacao_estoque", "movimentacao_financeira", "conta_pagar",
            "migracao_legado_mapa");

    /** Resultado do cenário único (ver javadoc da classe). */
    protected record Cenario(ResultadoMigracao dryRun, Map<String, Long> antesDryRun, Map<String, Long> depoisDryRun,
            long auditoriaAntesDryRun, long auditoriaDepoisDryRun, ResultadoMigracao falha, ResultadoMigracao real,
            ResultadoMigracao repeticao, Map<String, Long> antesRepeticao, Map<String, Long> depoisRepeticao,
            OpcoesMigracao opcoesReais) {
    }

    protected static Cenario cenario;
    private static NamedParameterJdbcTemplate legado;

    @Autowired
    protected MigracaoLegadoService migracao;

    @Autowired
    private MySQLContainer mysql;

    @BeforeEach
    void garantirCenario() throws Exception {
        if (cenario == null) {
            cenario = montarCenario();
        }
    }

    protected NamedParameterJdbcTemplate legado() {
        return legado;
    }

    protected static Path diretorio(String nome) {
        return Path.of("target", "it-migracao", nome).toAbsolutePath();
    }

    protected Map<String, Long> contagens() {
        Map<String, Long> m = new LinkedHashMap<>();
        for (String t : TABELAS_NEGOCIO) {
            m.put(t, jdbc.queryForObject("SELECT COUNT(*) FROM " + t, Long.class));
        }
        return m;
    }

    /** Id novo de um registro legado (pelo mapa). */
    protected Long idNovo(String entidade, long idLegado) {
        return jdbc.queryForObject("SELECT id_novo FROM migracao_legado_mapa WHERE entidade = ? AND id_legado = ?",
                Long.class, entidade, idLegado);
    }

    protected String situacao(String entidade, long idLegado) {
        return jdbc.queryForObject("SELECT situacao FROM migracao_legado_mapa WHERE entidade = ? AND id_legado = ?",
                String.class, entidade, idLegado);
    }

    protected String motivo(String entidade, long idLegado) {
        return jdbc.queryForObject("SELECT motivo FROM migracao_legado_mapa WHERE entidade = ? AND id_legado = ?",
                String.class, entidade, idLegado);
    }

    private Cenario montarCenario() throws Exception {
        prepararLegado();
        Path fotos = prepararFotos();
        Path relatorios = diretorio("relatorios");
        OpcoesMigracao dry = new OpcoesMigracao(true, 2, relatorios, fotos, false, null);
        OpcoesMigracao reais = new OpcoesMigracao(false, 2, relatorios, fotos, false, null);

        Map<String, Long> antesDry = contagens();
        long auditoriaAntes = auditoria.count();
        ResultadoMigracao dryRun = migracao.executar(dry, legado);
        Map<String, Long> depoisDry = contagens();
        long auditoriaDepois = auditoria.count();

        JdbcTemplate root = root();
        root.execute("REVOKE SELECT ON " + BANCO_LEGADO + ".venda FROM '" + USUARIO_LEITURA + "'@'%'");
        ResultadoMigracao falha = migracao.executar(reais, legado);
        root.execute("GRANT SELECT ON " + BANCO_LEGADO + ".venda TO '" + USUARIO_LEITURA + "'@'%'");

        ResultadoMigracao real = migracao.executar(reais, legado);
        Map<String, Long> antesRep = contagens();
        ResultadoMigracao repeticao = migracao.executar(reais, legado);
        Map<String, Long> depoisRep = contagens();
        return new Cenario(dryRun, antesDry, depoisDry, auditoriaAntes, auditoriaDepois, falha, real, repeticao,
                antesRep, depoisRep, reais);
    }

    private JdbcTemplate root() {
        return new JdbcTemplate(new DriverManagerDataSource(mysql.getJdbcUrl(), "root", mysql.getPassword()));
    }

    private void prepararLegado() throws SQLException {
        JdbcTemplate root = root();
        root.execute("DROP DATABASE IF EXISTS " + BANCO_LEGADO);
        root.execute("CREATE DATABASE " + BANCO_LEGADO + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        root.execute("DROP USER IF EXISTS '" + USUARIO_LEITURA + "'@'%'");
        root.execute("CREATE USER '" + USUARIO_LEITURA + "'@'%' IDENTIFIED BY '" + SENHA_LEITURA + "'");
        String urlLegado = mysql.getJdbcUrl().replace("/" + mysql.getDatabaseName(), "/" + BANCO_LEGADO);
        DriverManagerDataSource dsRoot = new DriverManagerDataSource(urlLegado, "root", mysql.getPassword());
        try (Connection c = dsRoot.getConnection()) {
            ScriptUtils.executeSqlScript(c, new ClassPathResource("legacy/01-schema-legado.sql"));
            ScriptUtils.executeSqlScript(c, new ClassPathResource("legacy/02-fixture-legado.sql"));
        }
        // SELECT por tabela (e não no banco todo) para o cenário conseguir revogar só uma.
        for (String t : root.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = ?",
                String.class, BANCO_LEGADO)) {
            root.execute("GRANT SELECT ON " + BANCO_LEGADO + "." + t + " TO '" + USUARIO_LEITURA + "'@'%'");
        }
        legado = new NamedParameterJdbcTemplate(new DriverManagerDataSource(urlLegado, USUARIO_LEITURA, SENHA_LEITURA));
    }

    private static Path prepararFotos() throws IOException {
        Path raiz = diretorio("fotos");
        Path pets = raiz.resolve("uploads/pets");
        Files.createDirectories(pets);
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", pets.resolve("rex.png").toFile());
        // Fora da raiz: o path traversal da fixture ("../../segredo.txt") não pode alcançá-lo.
        Files.writeString(raiz.getParent().getParent().resolve("segredo.txt"), "não importar", StandardCharsets.UTF_8);
        return raiz;
    }
}
