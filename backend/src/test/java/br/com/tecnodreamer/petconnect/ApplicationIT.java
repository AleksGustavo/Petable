package br.com.tecnodreamer.petconnect;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sobe o contexto: Flyway aplica as migrations e o Hibernate valida (ddl-auto=validate)
 * que as entidades batem com o schema.
 */
class ApplicationIT extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void migrationsAplicadasComDadosDeReferencia() {
        assertThat(jdbc.queryForList("select nome from papel", String.class))
                .containsExactlyInAnyOrder("ADMIN", "VENDAS", "SERVICO");
        assertThat(jdbc.queryForObject("select count(*) from especie", Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("select count(*) from preco_servico_porte", Integer.class)).isEqualTo(9);
    }

    @Test
    void adminInicialCriadoPeloBootstrap() {
        String papel = jdbc.queryForObject(
                "select p.nome from usuario u join papel p on p.id = u.papel_id where u.username = ?",
                String.class, ADMIN_USER);
        assertThat(papel).isEqualTo("ADMIN");
    }
}
