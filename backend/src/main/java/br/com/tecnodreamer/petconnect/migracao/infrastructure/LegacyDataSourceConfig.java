package br.com.tecnodreamer.petconnect.migracao.infrastructure;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Só existe com petconnect.migracao.legado=true (DA-03, DA-06). Nunca @Primary. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "petconnect.migracao.legado", havingValue = "true")
public class LegacyDataSourceConfig {

    @Bean(destroyMethod = "close")
    LegadoConexao legacyJdbcTemplate(@Value("${spring.datasource.legacy.url:}") String url,
            @Value("${spring.datasource.legacy.username:}") String usuario,
            @Value("${spring.datasource.legacy.password:}") String senha,
            @Value("${spring.datasource.legacy.driver-class-name:com.mysql.cj.jdbc.Driver}") String driver) {
        if (url.isBlank() || usuario.isBlank() || senha.isBlank()) {
            // Sem valores na mensagem: só o que falta configurar.
            throw new IllegalStateException(
                    "Migração habilitada sem LEGADO_DB_URL, LEGADO_DB_USERNAME e LEGADO_DB_PASSWORD configurados.");
        }
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName("legado-somente-leitura");
        ds.setJdbcUrl(url);
        ds.setUsername(usuario);
        ds.setPassword(senha);
        ds.setDriverClassName(driver);
        ds.setReadOnly(true);
        ds.setMaximumPoolSize(2);
        return new LegadoConexao(ds);
    }
}
