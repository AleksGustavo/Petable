package br.com.tecnodreamer.petconnect.migracao.infrastructure;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Conexão SOMENTE LEITURA com o banco legado. Não é um bean do tipo DataSource de
 * propósito: um segundo DataSource no contexto desligaria a autoconfiguração do banco
 * principal. As conexões saem com readOnly=true (o MySQL recusa escrita) e o runbook
 * exige um usuário SQL só com SELECT.
 */
public final class LegadoConexao implements AutoCloseable {

    private final HikariDataSource dataSource;
    private final NamedParameterJdbcTemplate jdbc;

    LegadoConexao(HikariDataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    public NamedParameterJdbcTemplate jdbc() {
        return jdbc;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
