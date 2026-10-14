package br.com.tecnodreamer.petconnect.produto.application;

import br.com.tecnodreamer.petconnect.produto.web.ReconciliacaoEstoqueResponse;
import br.com.tecnodreamer.petconnect.produto.web.ReconciliacaoEstoqueResponse.Divergencia;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Reconciliação do estoque em lote (invariantes I1, I2, I3, I5 e I7 de {@code 01_DECISOES_LOTES.md}). Só lê: consultas
 * agregadas com limite por regra, seguras para rodar com o sistema em uso (monitoramento e homologação). O valor de cada
 * divergência (esperado x encontrado) permite corrigir com um ajuste explícito e auditado, nunca editando o histórico.
 */
@Service
public class ReconciliacaoEstoqueService {

    static final int LIMITE_POR_REGRA = 200;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ReconciliacaoEstoqueService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReconciliacaoEstoqueResponse reconciliar() {
        List<Divergencia> divergencias = new ArrayList<>();
        boolean truncado = false;

        // I1: o total do produto é a soma dos movimentos, exceto a regularização (que só move saldo para dentro de um lote).
        truncado |= coletar(divergencias, """
                select p.id as produto_id, null as lote_id, null as ref_id, coalesce(sum(m.quantidade), 0) as esperado, p.quantidade_estoque as encontrado
                from produto p left join movimentacao_estoque m on m.produto_id = p.id and m.tipo <> 'REGULARIZACAO_LOTE'
                group by p.id, p.quantidade_estoque
                having p.quantidade_estoque <> coalesce(sum(m.quantidade), 0)
                order by p.id limit ?""", "I1", "O total do produto difere da soma dos movimentos");

        // I2: o saldo do lote é a soma dos movimentos do lote.
        truncado |= coletar(divergencias, """
                select l.produto_id, l.id as lote_id, null as ref_id, coalesce(sum(m.quantidade), 0) as esperado, l.saldo as encontrado
                from lote_estoque l left join movimentacao_estoque m on m.lote_id = l.id
                group by l.id, l.produto_id, l.saldo
                having l.saldo <> coalesce(sum(m.quantidade), 0)
                order by l.id limit ?""", "I2", "O saldo do lote difere da soma dos movimentos do lote");

        // I3: produto com controle de lote: o total é a soma dos lotes (não existe saldo sem lote).
        truncado |= coletar(divergencias, """
                select p.id as produto_id, null as lote_id, null as ref_id, coalesce(sum(l.saldo), 0) as esperado, p.quantidade_estoque as encontrado
                from produto p left join lote_estoque l on l.produto_id = p.id
                where p.controla_lote = true
                group by p.id, p.quantidade_estoque
                having p.quantidade_estoque <> coalesce(sum(l.saldo), 0)
                order by p.id limit ?""", "I3", "O total do produto controlado difere da soma dos lotes");

        // I5: item de venda que saiu de lote: a soma das alocações é a quantidade do item.
        truncado |= coletar(divergencias, """
                select i.produto_id, null as lote_id, i.id as ref_id, i.quantidade as esperado, sum(a.quantidade) as encontrado
                from item_venda i join item_venda_lote a on a.item_venda_id = i.id
                group by i.id, i.produto_id, i.quantidade
                having sum(a.quantidade) <> i.quantidade
                order by i.id limit ?""", "I5", "A alocação em lotes não soma a quantidade do item vendido");

        // I7: movimento SEM lote criado depois que o produto já tinha lote = saída/entrada que ignorou o lote.
        truncado |= coletar(divergencias, """
                select m.produto_id, null as lote_id, m.id as ref_id, 0 as esperado, 1 as encontrado
                from movimentacao_estoque m join produto p on p.id = m.produto_id
                where m.lote_id is null and p.controla_lote = true
                  and exists (select 1 from movimentacao_estoque x where x.produto_id = m.produto_id and x.lote_id is not null and x.id < m.id)
                order by m.id limit ?""", "I7", "Movimento sem lote em produto que já controla lote");

        long produtos = contar("select count(*) from produto");
        long controlados = contar("select count(*) from produto where controla_lote = true");
        long lotes = contar("select count(*) from lote_estoque");
        return new ReconciliacaoEstoqueResponse(divergencias.isEmpty(), truncado, Instant.now(clock), produtos, controlados, lotes,
                List.copyOf(divergencias));
    }

    /** Roda a consulta com limite+1 para saber se passou do limite; devolve se houve truncamento. */
    private boolean coletar(List<Divergencia> destino, String sql, String regra, String descricao) {
        List<Divergencia> achadas = jdbc.query(sql, (rs, n) -> new Divergencia(regra, descricao, rs.getLong("produto_id"),
                valorOuNulo(rs.getObject("lote_id")), valorOuNulo(rs.getObject("ref_id")), rs.getLong("esperado"), rs.getLong("encontrado")),
                LIMITE_POR_REGRA + 1);
        boolean truncou = achadas.size() > LIMITE_POR_REGRA;
        destino.addAll(truncou ? achadas.subList(0, LIMITE_POR_REGRA) : achadas);
        return truncou;
    }

    private static Long valorOuNulo(Object o) {
        return o instanceof Number n ? n.longValue() : null;
    }

    private long contar(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }
}
