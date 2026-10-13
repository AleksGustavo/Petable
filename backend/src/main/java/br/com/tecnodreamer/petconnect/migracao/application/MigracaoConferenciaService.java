package br.com.tecnodreamer.petconnect.migracao.application;

import br.com.tecnodreamer.petconnect.venda.domain.FormaPagamento;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Conferências automáticas (§15, itens 1–11). Os itens 12–15 dependem da API/relatório
 * e são provados nos testes de integração. Qualquer falha aqui é crítica: a execução
 * termina FALHOU. Detalhes só com números.
 */
@Service
public class MigracaoConferenciaService {

    private static final String MAPA_VENDA = "migracao_legado_mapa m WHERE m.entidade = 'venda' AND m.id_novo IS NOT NULL";
    private static final int BLOCO = 1000;

    private final NamedParameterJdbcTemplate jdbc;

    public MigracaoConferenciaService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Conferencia> conferir(ResultadoMigracao r, OpcoesMigracao o, NamedParameterJdbcTemplate legado) {
        List<Conferencia> c = new ArrayList<>();
        long abertas = r.entidades().stream().filter(e -> !e.fecha()).count();
        c.add(new Conferencia("contagens", abertas == 0, abertas + " entidade(s) com contagem que não fecha"));

        long duplicados = numero("""
                SELECT COUNT(*) FROM (SELECT entidade, id_legado FROM migracao_legado_mapa
                 GROUP BY entidade, id_legado HAVING COUNT(*) > 1) x""", Map.of());
        c.add(new Conferencia("mapa sem duplicidade", duplicados == 0, duplicados + " duplicado(s)"));

        Map<Long, Long> vendas = pares("SELECT m.id_legado, m.id_novo FROM " + MAPA_VENDA, Map.of());
        BigDecimal somaLegado = somaLegado(legado, "SELECT COALESCE(SUM(valor_total), 0) FROM venda WHERE id IN (:ids)",
                vendas.keySet());
        BigDecimal somaNova = decimal("SELECT COALESCE(SUM(v.valor_total), 0) FROM venda v JOIN migracao_legado_mapa m "
                + "ON m.entidade = 'venda' AND m.id_novo = v.id", Map.of());
        long semChave = numero("SELECT COUNT(*) FROM venda v JOIN migracao_legado_mapa m ON m.entidade = 'venda' "
                + "AND m.id_novo = v.id WHERE v.chave_idempotencia <> CONCAT('LEGADO:', m.id_legado)", Map.of());
        c.add(new Conferencia("vendas", somaLegado.compareTo(somaNova) == 0 && semChave == 0,
                "legado " + somaLegado + " / novo " + somaNova + " em " + vendas.size() + " venda(s)"));

        BigDecimal itensLegado = somaLegado(legado, "SELECT COUNT(*) FROM item_venda WHERE venda_id IN (:ids)",
                vendas.keySet());
        long itensNovos = numero("SELECT COUNT(*) FROM item_venda i JOIN migracao_legado_mapa m ON m.entidade = 'venda' "
                + "AND m.id_novo = i.venda_id", Map.of());
        c.add(new Conferencia("itens de venda", itensLegado.longValue() == itensNovos,
                "legado " + itensLegado.longValue() + " / novo " + itensNovos));

        c.add(conferirEstoque(r, legado));
        long negativos = numero("SELECT COUNT(*) FROM produto WHERE quantidade_estoque < 0", Map.of());
        c.add(new Conferencia("nenhum saldo negativo", negativos == 0, negativos + " produto(s)"));

        long saidas = numero("SELECT COUNT(*) FROM movimentacao_estoque me JOIN migracao_legado_mapa m "
                + "ON m.entidade = 'venda' AND m.id_novo = me.venda_id WHERE me.tipo = 'SAIDA_VENDA'", Map.of());
        c.add(new Conferencia("venda histórica sem SAIDA_VENDA", saidas == 0, saidas + " movimento(s)"));
        long entradas = numero("SELECT COUNT(*) FROM movimentacao_estoque me JOIN migracao_legado_mapa m "
                + "ON m.entidade = 'compra' AND m.id_novo = me.compra_id WHERE me.tipo = 'ENTRADA_COMPRA'", Map.of());
        c.add(new Conferencia("compra histórica sem ENTRADA_COMPRA", entradas == 0, entradas + " movimento(s)"));

        long vendasSemLanc = numero("SELECT COUNT(*) FROM " + MAPA_VENDA + " AND NOT EXISTS (SELECT 1 FROM "
                + "movimentacao_financeira f WHERE f.chave_origem = CONCAT('VENDA:', m.id_novo))", Map.of());
        c.add(new Conferencia("backfill das vendas", vendasSemLanc == 0, vendasSemLanc + " venda(s) sem lançamento"));

        FormaPagamento forma = o.formaServicoBackfill();
        long servicosSemLanc = numero("SELECT COUNT(*) FROM migracao_legado_mapa m JOIN servico_realizado s "
                + "ON m.entidade = 'servico_realizado' AND m.id_novo = s.id WHERE s.valor > 0 AND NOT EXISTS "
                + "(SELECT 1 FROM movimentacao_financeira f WHERE f.chave_origem = CONCAT('SERVICO:', s.id))", Map.of());
        c.add(forma == null
                ? new Conferencia("backfill dos serviços", true,
                        servicosSemLanc + " serviço(s) aguardam forma de pagamento (não configurada)")
                : new Conferencia("backfill dos serviços", servicosSemLanc == 0, servicosSemLanc + " sem lançamento"));

        long duplicadoFin = numero("SELECT COUNT(*) FROM movimentacao_financeira WHERE chave_origem LIKE 'LEGADO:%' "
                + "AND categoria IN ('VENDA_PRODUTO', 'SERVICO')", Map.of());
        long contas = numero("SELECT COUNT(*) FROM conta_pagar c JOIN migracao_legado_mapa m ON m.entidade = 'compra' "
                + "AND m.id_novo = c.compra_id", Map.of());
        c.add(new Conferencia("financeiro sem duplicidade", duplicadoFin == 0 && contas == 0,
                duplicadoFin + " lançamento(s) duplicado(s), " + contas + " conta(s) a pagar de compra histórica"));
        return c;
    }

    /** Só produtos ajustados NESTA execução: depois da virada o saldo muda com o uso normal. */
    private Conferencia conferirEstoque(ResultadoMigracao r, NamedParameterJdbcTemplate legado) {
        Map<Long, Long> produtos = pares("SELECT id_legado, id_novo FROM migracao_legado_mapa WHERE entidade = "
                + "'produto_estoque' AND id_novo IS NOT NULL AND execucao_id = :e", Map.of("e", r.execucaoId()));
        Map<Long, Integer> legadoSaldo = new HashMap<>();
        for (List<Long> bloco : blocos(produtos.keySet())) {
            legado.query("SELECT id, quantidade_estoque FROM produto WHERE id IN (:ids)", Map.of("ids", bloco),
                    rs -> {
                        legadoSaldo.put(rs.getLong("id"), Math.max(rs.getInt("quantidade_estoque"), 0));
                    });
        }
        Map<Long, Integer> novoSaldo = new HashMap<>();
        for (List<Long> bloco : blocos(produtos.values())) {
            jdbc.query("SELECT id, quantidade_estoque FROM produto WHERE id IN (:ids)", Map.of("ids", bloco),
                    rs -> {
                        novoSaldo.put(rs.getLong("id"), rs.getInt("quantidade_estoque"));
                    });
        }
        long divergentes = produtos.entrySet().stream()
                .filter(e -> !legadoSaldo.getOrDefault(e.getKey(), 0).equals(novoSaldo.get(e.getValue()))).count();
        return new Conferencia("estoque", divergentes == 0,
                divergentes + " de " + produtos.size() + " produto(s) com saldo divergente");
    }

    private long numero(String sql, Map<String, ?> p) {
        Long n = jdbc.queryForObject(sql, p, Long.class);
        return n == null ? 0 : n;
    }

    private BigDecimal decimal(String sql, Map<String, ?> p) {
        BigDecimal d = jdbc.queryForObject(sql, p, BigDecimal.class);
        return d == null ? BigDecimal.ZERO : d;
    }

    private Map<Long, Long> pares(String sql, Map<String, ?> p) {
        Map<Long, Long> m = new HashMap<>();
        jdbc.query(sql, p, rs -> {
            m.put(rs.getLong(1), rs.getLong(2));
        });
        return m;
    }

    private static BigDecimal somaLegado(NamedParameterJdbcTemplate legado, String sql, java.util.Collection<Long> ids) {
        BigDecimal total = BigDecimal.ZERO;
        for (List<Long> bloco : blocos(ids)) {
            BigDecimal parcial = legado.queryForObject(sql, Map.of("ids", bloco), BigDecimal.class);
            total = total.add(parcial == null ? BigDecimal.ZERO : parcial);
        }
        return total.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static List<List<Long>> blocos(java.util.Collection<Long> ids) {
        List<Long> lista = new ArrayList<>(ids);
        List<List<Long>> r = new ArrayList<>();
        for (int i = 0; i < lista.size(); i += BLOCO) {
            r.add(lista.subList(i, Math.min(lista.size(), i + BLOCO)));
        }
        return r;
    }
}
