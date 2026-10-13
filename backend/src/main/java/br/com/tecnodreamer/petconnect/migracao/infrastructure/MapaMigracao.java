package br.com.tecnodreamer.petconnect.migracao.infrastructure;

import br.com.tecnodreamer.petconnect.migracao.application.Situacao;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Mapa legado → novo (V10), base da idempotência. Cache por entidade, recarregado a
 * cada execução ({@link #limpar()}): no dry-run as linhas vivem só na transação desfeita.
 */
@Component
public class MapaMigracao {

    public record Registro(Long idNovo, Situacao situacao) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final Map<String, Map<Long, Registro>> cache = new HashMap<>();

    public MapaMigracao(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public synchronized void limpar() {
        cache.clear();
    }

    public synchronized Optional<Registro> buscar(String entidade, Long idLegado) {
        return Optional.ofNullable(daEntidade(entidade).get(idLegado));
    }

    public synchronized void gravar(String entidade, Long idLegado, Long idNovo, Situacao situacao, String motivo,
            Long execucaoId) {
        jdbc.update("""
                INSERT INTO migracao_legado_mapa (entidade, id_legado, id_novo, situacao, motivo, execucao_id)
                VALUES (:entidade, :idLegado, :idNovo, :situacao, :motivo, :execucaoId)
                """, new MapSqlParameterSource()
                .addValue("entidade", entidade)
                .addValue("idLegado", idLegado)
                .addValue("idNovo", idNovo)
                .addValue("situacao", situacao.name())
                .addValue("motivo", motivo)
                .addValue("execucaoId", execucaoId));
        daEntidade(entidade).put(idLegado, new Registro(idNovo, situacao));
    }

    /** Lote desfeito (execução real): o cache não pode guardar o que voltou atrás. */
    public synchronized void descartar(String entidade) {
        cache.remove(entidade);
    }

    private Map<Long, Registro> daEntidade(String entidade) {
        return cache.computeIfAbsent(entidade, e -> {
            Map<Long, Registro> m = new HashMap<>();
            jdbc.query("SELECT id_legado, id_novo, situacao FROM migracao_legado_mapa WHERE entidade = :e", Map.of("e", e),
                    rs -> {
                        long novo = rs.getLong("id_novo");
                        m.put(rs.getLong("id_legado"), new Registro(rs.wasNull() ? null : novo,
                                Situacao.valueOf(rs.getString("situacao"))));
                    });
            return m;
        });
    }
}
