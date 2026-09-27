package br.com.tecnodreamer.petconnect.relatorio.application;

import br.com.tecnodreamer.petconnect.relatorio.web.Relatorios;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static br.com.tecnodreamer.petconnect.relatorio.application.RelatorioCalculos.dinheiro;

/**
 * Relatórios gerenciais (etapa 07): SOMENTE leitura, agregação no banco com
 * parâmetros nomeados (DA-01). Faturamento só de vendas CONCLUIDA (DA-03).
 */
@Service
@Transactional(readOnly = true)
public class RelatorioService {

    public static final String AVISO_CUSTO = "Custo estimado pelo custo médio ATUAL do produto: o sistema não grava custo por item vendido.";
    public static final String AVISO_GIRO = "Giro e cobertura aproximados com o saldo ATUAL e o custo médio ATUAL de cada produto.";
    static final List<String> CATEGORIAS_DESPESA = List.of("COMPRA_MERCADORIA", "DESPESA_FUNCIONARIO", "DESPESA_OPERACIONAL", "OUTROS");

    private static final String VENDAS_CONCLUIDAS = " v.status = 'CONCLUIDA' and v.data_venda >= :ini and v.data_venda < :fim ";

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public RelatorioService(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    private static MapSqlParameterSource params(Periodo p) {
        return new MapSqlParameterSource().addValue("ini", p.inicioDataHora()).addValue("fim", p.fimExclusivo())
                .addValue("dIni", p.inicio()).addValue("dFim", p.fim());
    }

    private static BigDecimal dec(ResultSet rs, int col) throws SQLException {
        return dinheiro(rs.getBigDecimal(col));
    }

    // ------------------------------------------------------------------ vendas

    public Relatorios.Vendas vendas(Periodo p) {
        MapSqlParameterSource ps = params(p);
        Object[] t = jdbc.queryForObject("select count(*), coalesce(sum(v.valor_total + v.desconto), 0), coalesce(sum(v.desconto), 0), "
                + "coalesce(sum(v.valor_total), 0) from venda v where" + VENDAS_CONCLUIDAS, ps,
                (rs, i) -> new Object[] {rs.getLong(1), dec(rs, 2), dec(rs, 3), dec(rs, 4)});
        long quantidade = (long) t[0];
        BigDecimal liquido = (BigDecimal) t[3];
        Relatorios.Contagem canceladas = jdbc.queryForObject("select count(*), coalesce(sum(v.valor_total), 0) from venda v "
                + "where v.status = 'CANCELADA' and v.data_venda >= :ini and v.data_venda < :fim", ps,
                (rs, i) -> new Relatorios.Contagem(rs.getLong(1), dec(rs, 2)));
        Map<LocalDate, Relatorios.VendasDia> porDia = jdbc.query("select date(v.data_venda), count(*), sum(v.valor_total) from venda v "
                + "where" + VENDAS_CONCLUIDAS + "group by date(v.data_venda)", ps,
                (rs, i) -> new Relatorios.VendasDia(rs.getObject(1, LocalDate.class), rs.getLong(2), dec(rs, 3)))
                .stream().collect(Collectors.toMap(Relatorios.VendasDia::data, Function.identity()));
        List<Relatorios.VendasGrupo> porForma = jdbc.query("select v.forma_pagamento, count(*), sum(v.valor_total) from venda v where"
                + VENDAS_CONCLUIDAS + "group by v.forma_pagamento order by 3 desc, 1", ps, RelatorioService::grupo);
        List<Relatorios.VendasGrupo> porOperador = jdbc.query("select coalesce(u.username, '(sem operador)'), count(*), "
                + "sum(v.valor_total) from venda v left join usuario u on u.id = v.usuario_id where" + VENDAS_CONCLUIDAS
                + "group by coalesce(u.username, '(sem operador)') order by 3 desc, 1", ps, RelatorioService::grupo);
        return new Relatorios.Vendas(p.inicio(), p.fim(), quantidade, (BigDecimal) t[1], (BigDecimal) t[2], liquido,
                RelatorioCalculos.ticketMedio(liquido, quantidade), canceladas,
                RelatorioCalculos.preencherDias(p, porDia, d -> new Relatorios.VendasDia(d, 0, RelatorioCalculos.zero())),
                porForma, porOperador);
    }

    private static Relatorios.VendasGrupo grupo(ResultSet rs, int i) throws SQLException {
        return new Relatorios.VendasGrupo(rs.getString(1), rs.getLong(2), dec(rs, 3));
    }

    // ------------------------------------------------------------------ produtos

    private record Vendido(Long id, String nome, String categoria, long quantidade, BigDecimal faturamento) {
    }

    private List<Vendido> vendidos(Periodo p, String ordem, Integer limite) {
        MapSqlParameterSource ps = params(p);
        String sql = "select pr.id, pr.nome, c.nome, sum(i.quantidade), sum(i.subtotal) from item_venda i "
                + "join venda v on v.id = i.venda_id join produto pr on pr.id = i.produto_id "
                + "join categoria_produto c on c.id = pr.categoria_id where" + VENDAS_CONCLUIDAS
                + "group by pr.id, pr.nome, c.nome order by " + ordem;
        if (limite != null) {
            sql += " limit :limite";
            ps.addValue("limite", limite);
        }
        return jdbc.query(sql, ps, (rs, i) -> new Vendido(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4), dec(rs, 5)));
    }

    private BigDecimal faturamentoTotal(Periodo p) {
        return dinheiro(jdbc.queryForObject("select coalesce(sum(i.subtotal), 0) from item_venda i join venda v on v.id = i.venda_id "
                + "where" + VENDAS_CONCLUIDAS, params(p), BigDecimal.class));
    }

    public Relatorios.MaisVendidos maisVendidos(Periodo p, int limite) {
        BigDecimal total = faturamentoTotal(p);
        List<Relatorios.ProdutoVendido> itens = vendidos(p, "4 desc, 5 desc, pr.id", limite).stream()
                .map(v -> new Relatorios.ProdutoVendido(v.id(), v.nome(), v.categoria(), v.quantidade(), v.faturamento(),
                        RelatorioCalculos.percentual(v.faturamento(), total)))
                .toList();
        return new Relatorios.MaisVendidos(p.inicio(), p.fim(), itens, total);
    }

    public Relatorios.CurvaAbc curvaAbc(Periodo p) {
        List<RelatorioCalculos.ItemAbc<Vendido>> abc = RelatorioCalculos.curvaAbc(vendidos(p, "5 desc, pr.id", null), Vendido::faturamento);
        BigDecimal total = abc.stream().map(RelatorioCalculos.ItemAbc::faturamento).reduce(RelatorioCalculos.zero(), BigDecimal::add);
        List<Relatorios.ItemAbc> itens = abc.stream().map(a -> new Relatorios.ItemAbc(a.item().id(), a.item().nome(), a.faturamento(),
                a.participacao(), a.acumulado(), a.classe())).toList();
        List<Relatorios.ResumoAbc> resumo = new ArrayList<>();
        for (String classe : List.of("A", "B", "C")) {
            List<Relatorios.ItemAbc> da = itens.stream().filter(i -> i.classe().equals(classe)).toList();
            BigDecimal f = da.stream().map(Relatorios.ItemAbc::faturamento).reduce(RelatorioCalculos.zero(), BigDecimal::add);
            resumo.add(new Relatorios.ResumoAbc(classe, da.size(), f, RelatorioCalculos.percentual(f, total)));
        }
        return new Relatorios.CurvaAbc(p.inicio(), p.fim(), itens, resumo, total);
    }

    // ------------------------------------------------------------------ estoque e margem (ADMIN)

    public Relatorios.Estoque estoque(Periodo p) {
        List<Relatorios.ItemEstoque> itens = new ArrayList<>();
        long[] semCusto = {0};
        jdbc.query("select pr.id, pr.nome, pr.quantidade_estoque, pr.preco_custo, coalesce(vd.q, 0) from produto pr "
                + "left join (select i.produto_id, sum(i.quantidade) q from item_venda i join venda v on v.id = i.venda_id where"
                + VENDAS_CONCLUIDAS + "group by i.produto_id) vd on vd.produto_id = pr.id where pr.ativo = true order by pr.nome, pr.id",
                params(p), rs -> {
                    BigDecimal custo = rs.getBigDecimal(4);
                    if (custo == null) {
                        semCusto[0]++;
                    }
                    long saldo = rs.getLong(3);
                    long vendida = rs.getLong(5);
                    BigDecimal c = dinheiro(custo);
                    itens.add(new Relatorios.ItemEstoque(rs.getLong(1), rs.getString(2), saldo, c,
                            dinheiro(c.multiply(BigDecimal.valueOf(saldo))), vendida, RelatorioCalculos.giro(vendida, saldo),
                            RelatorioCalculos.coberturaDias(saldo, vendida, p.dias())));
                });
        BigDecimal total = itens.stream().map(Relatorios.ItemEstoque::valorEstoque).reduce(RelatorioCalculos.zero(), BigDecimal::add);
        return new Relatorios.Estoque(p.inicio(), p.fim(), itens, total, semCusto[0], AVISO_GIRO);
    }

    public Relatorios.Margem margem(Periodo p) {
        List<Relatorios.ItemMargem> itens = new ArrayList<>();
        long[] semCusto = {0};
        jdbc.query("select pr.id, pr.nome, sum(i.quantidade), sum(i.subtotal), pr.preco_custo from item_venda i "
                + "join venda v on v.id = i.venda_id join produto pr on pr.id = i.produto_id where" + VENDAS_CONCLUIDAS
                + "group by pr.id, pr.nome, pr.preco_custo order by 4 desc, pr.id", params(p), rs -> {
                    BigDecimal custoUnit = rs.getBigDecimal(5);
                    if (custoUnit == null) {
                        semCusto[0]++;
                    }
                    long q = rs.getLong(3);
                    BigDecimal receita = dec(rs, 4);
                    BigDecimal custo = dinheiro(dinheiro(custoUnit).multiply(BigDecimal.valueOf(q)));
                    BigDecimal margem = receita.subtract(custo);
                    itens.add(new Relatorios.ItemMargem(rs.getLong(1), rs.getString(2), q, receita, custo, margem,
                            RelatorioCalculos.margemPercentual(margem, receita)));
                });
        BigDecimal receita = itens.stream().map(Relatorios.ItemMargem::receita).reduce(RelatorioCalculos.zero(), BigDecimal::add);
        BigDecimal custo = itens.stream().map(Relatorios.ItemMargem::custoEstimado).reduce(RelatorioCalculos.zero(), BigDecimal::add);
        BigDecimal margem = receita.subtract(custo);
        return new Relatorios.Margem(p.inicio(), p.fim(), receita, custo, margem, RelatorioCalculos.margemPercentual(margem, receita),
                semCusto[0], itens, AVISO_CUSTO);
    }

    // ------------------------------------------------------------------ serviços, despesas, caixas, contas (ADMIN)

    public Relatorios.Servicos servicos(Periodo p) {
        MapSqlParameterSource ps = params(p);
        String filtro = " where sr.data_servico >= :ini and sr.data_servico < :fim ";
        Relatorios.Contagem t = jdbc.queryForObject("select count(*), coalesce(sum(sr.valor), 0) from servico_realizado sr" + filtro, ps,
                (rs, i) -> new Relatorios.Contagem(rs.getLong(1), dec(rs, 2)));
        List<Relatorios.VendasGrupo> porServico = jdbc.query("select s.nome, count(*), sum(sr.valor) from servico_realizado sr "
                + "join servico s on s.id = sr.servico_id" + filtro + "group by s.nome order by 3 desc, 1", ps, RelatorioService::grupo);
        List<Relatorios.VendasGrupo> porFuncionario = jdbc.query("select coalesce(f.nome, 'Sem funcionário'), count(*), sum(sr.valor) "
                + "from servico_realizado sr left join funcionario f on f.id = sr.funcionario_id" + filtro
                + "group by coalesce(f.nome, 'Sem funcionário') order by 3 desc, 1", ps, RelatorioService::grupo);
        return new Relatorios.Servicos(p.inicio(), p.fim(), t.quantidade(), t.valor(), porServico, porFuncionario);
    }

    /** DA-07: saídas − estornos (entradas com estorno_de_id) por categoria de despesa; sem sangria/suprimento. */
    public Relatorios.Despesas despesas(Periodo p) {
        MapSqlParameterSource ps = params(p).addValue("categorias", CATEGORIAS_DESPESA);
        List<Relatorios.DespesaCategoria> porCategoria = jdbc.query("select m.categoria, "
                + "coalesce(sum(case when m.tipo = 'SAIDA' then m.valor else 0 end), 0) "
                + "- coalesce(sum(case when m.tipo = 'ENTRADA' and m.estorno_de_id is not null then m.valor else 0 end), 0) "
                + "from movimentacao_financeira m where m.categoria in (:categorias) and m.data_movimentacao between :dIni and :dFim "
                + "and (m.chave_origem is null or m.chave_origem not like 'CAIXA\\_%') group by m.categoria order by 2 desc, 1", ps,
                (rs, i) -> new Relatorios.DespesaCategoria(rs.getString(1), dec(rs, 2)));
        BigDecimal total = porCategoria.stream().map(Relatorios.DespesaCategoria::valor).reduce(RelatorioCalculos.zero(), BigDecimal::add);
        return new Relatorios.Despesas(p.inicio(), p.fim(), total, porCategoria);
    }

    public Relatorios.Caixas caixas(Periodo p) {
        MapSqlParameterSource ps = params(p);
        String filtro = " from caixa c join usuario u on u.id = c.usuario_id where c.status = 'FECHADO' "
                + "and c.fechado_em >= :ini and c.fechado_em < :fim ";
        List<Relatorios.CaixaOperador> porOperador = jdbc.query("select u.username, count(*), coalesce(sum(c.diferenca_total), 0), "
                + "sum(case when c.diferenca_total < 0 then 1 else 0 end), sum(case when c.diferenca_total > 0 then 1 else 0 end)"
                + filtro + "group by u.username order by 3, 1", ps,
                (rs, i) -> new Relatorios.CaixaOperador(rs.getString(1), rs.getLong(2), dec(rs, 3), rs.getLong(4), rs.getLong(5)));
        List<Relatorios.CaixaFechado> lista = jdbc.query("select c.id, u.username, c.aberto_em, c.fechado_em, c.diferenca_total" + filtro
                + "order by c.fechado_em desc, c.id desc limit 200", ps,
                (rs, i) -> new Relatorios.CaixaFechado(rs.getLong(1), rs.getString(2), rs.getObject(3, LocalDateTime.class),
                        rs.getObject(4, LocalDateTime.class), dec(rs, 5)));
        long quantidade = porOperador.stream().mapToLong(Relatorios.CaixaOperador::caixas).sum();
        BigDecimal diferenca = porOperador.stream().map(Relatorios.CaixaOperador::diferenca).reduce(RelatorioCalculos.zero(), BigDecimal::add);
        return new Relatorios.Caixas(p.inicio(), p.fim(), quantidade, diferenca, porOperador, lista);
    }

    public Relatorios.Contas contas(Periodo p) {
        MapSqlParameterSource ps = params(p).addValue("hoje", LocalDate.now(clock));
        Relatorios.Contagem pagas = jdbc.queryForObject("select count(*), coalesce(sum(c.valor_original), 0) from conta_pagar c "
                + "where c.status = 'PAGA' and c.paga_em >= :ini and c.paga_em < :fim", ps,
                (rs, i) -> new Relatorios.Contagem(rs.getLong(1), dec(rs, 2)));
        Relatorios.Contagem vencidas = jdbc.queryForObject("select count(*), coalesce(sum(c.saldo), 0) from conta_pagar c "
                + "where c.status = 'ABERTA' and c.data_vencimento < :hoje", ps, (rs, i) -> new Relatorios.Contagem(rs.getLong(1), dec(rs, 2)));
        Relatorios.Contagem aVencer = jdbc.queryForObject("select count(*), coalesce(sum(c.saldo), 0) from conta_pagar c "
                + "where c.status = 'ABERTA' and c.data_vencimento >= :hoje", ps, (rs, i) -> new Relatorios.Contagem(rs.getLong(1), dec(rs, 2)));
        List<Relatorios.ContaPaga> itens = jdbc.query("select c.id, c.descricao, c.valor_original, c.paga_em, c.forma_pagamento "
                + "from conta_pagar c where c.status = 'PAGA' and c.paga_em >= :ini and c.paga_em < :fim order by c.paga_em desc, c.id desc limit 200",
                ps, (rs, i) -> new Relatorios.ContaPaga(rs.getLong(1), rs.getString(2), dec(rs, 3), rs.getObject(4, LocalDateTime.class),
                        rs.getString(5)));
        return new Relatorios.Contas(p.inicio(), p.fim(), pagas, vencidas, aVencer, itens);
    }
}
