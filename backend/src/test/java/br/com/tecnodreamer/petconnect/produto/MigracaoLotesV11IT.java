package br.com.tecnodreamer.petconnect.produto;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Migration V11 (lotes) sobre um banco no estado da V10 com dados legados representativos: produto com saldo,
 * compra recebida, venda e movimentos. Tem container próprio para poder parar a migração na V10 e depois subir
 * para a versão atual. Prova que a V11 é aditiva: nenhum dado existente muda e a escrita no formato antigo continua válida.
 */
class MigracaoLotesV11IT {

    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"));
    private static DriverManagerDataSource dataSource;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void subirBancoNaV10ComDadosLegados() {
        MYSQL.start();
        dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        flyway("10").migrate();
        semear();
    }

    @AfterAll
    static void derrubar() {
        MYSQL.stop();
    }

    @Test
    void migrationEhAditivaPreservaDadosEOldCodeContinuaEscrevendo() {
        List<Map<String, Object>> produtosAntes = jdbc.queryForList(
                "select id, nome, categoria_id, preco_custo, preco_venda, quantidade_estoque, estoque_minimo, ativo from produto order by id");
        List<Map<String, Object>> movimentosAntes = jdbc.queryForList(
                "select id, produto_id, tipo, quantidade, saldo_anterior, saldo_resultante, compra_id, venda_id, usuario_id, motivo from movimentacao_estoque order by id");
        List<Map<String, Object>> itensVendaAntes = jdbc.queryForList("select * from item_venda order by id");
        List<Map<String, Object>> itensCompraAntes = jdbc.queryForList("select * from item_compra order by id");
        assertThat(produtosAntes).hasSize(2);
        assertThat(movimentosAntes).hasSize(3);
        assertThat(versaoAplicada()).isEqualTo("10");

        // Este teste é da V11: para na V11 (a V12 tem o seu próprio teste de compatibilidade abaixo).
        flyway("11").migrate();

        assertThat(versaoAplicada()).isEqualTo("11");
        // Nada do que existia mudou.
        assertThat(jdbc.queryForList("select id, nome, categoria_id, preco_custo, preco_venda, quantidade_estoque, estoque_minimo, ativo from produto order by id"))
                .isEqualTo(produtosAntes);
        assertThat(jdbc.queryForList("select id, produto_id, tipo, quantidade, saldo_anterior, saldo_resultante, compra_id, venda_id, usuario_id, motivo from movimentacao_estoque order by id"))
                .isEqualTo(movimentosAntes);
        assertThat(jdbc.queryForList("select * from item_venda order by id")).isEqualTo(itensVendaAntes);
        assertThat(jdbc.queryForList("select * from item_compra order by id")).isEqualTo(itensCompraAntes);

        // Estado novo: tudo desligado, nada inventado.
        assertThat(jdbc.queryForObject("select count(*) from produto where controla_lote or permite_sem_validade or dias_bloqueio_validade is not null", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from movimentacao_estoque where lote_id is not null or lote_saldo_anterior is not null "
                + "or lote_saldo_resultante is not null or operacao_id is not null", Integer.class)).isZero();
        for (String tabela : List.of("lote_estoque", "item_venda_lote", "item_compra_lote")) {
            assertThat(jdbc.queryForObject("select count(*) from " + tabela, Integer.class)).as(tabela).isZero();
        }
        // Saldo global legado coincide com os movimentos (I1) e a reconciliação de lote dá vazio.
        assertThat(jdbc.queryForList("""
                select p.id from produto p
                where p.quantidade_estoque <> coalesce((select sum(m.quantidade) from movimentacao_estoque m
                                                        where m.produto_id = p.id and m.tipo <> 'REGULARIZACAO_LOTE'), 0)""")).isEmpty();
        assertThat(jdbc.queryForList("select id from lote_estoque l where l.saldo <> coalesce((select sum(quantidade) from movimentacao_estoque where lote_id = l.id), 0)")).isEmpty();

        // O código da versão anterior (que não conhece lote) segue gravando como antes.
        jdbc.update("insert into produto (nome, categoria_id, preco_venda) values ('Escrita antiga', (select min(id) from categoria_produto), 10.00)");
        long novo = jdbc.queryForObject("select max(id) from produto", Long.class);
        jdbc.update("insert into movimentacao_estoque (produto_id, tipo, quantidade, saldo_anterior, saldo_resultante, usuario_id) "
                + "values (?, 'ENTRADA_COMPRA', 3, 0, 3, (select min(id) from usuario))", novo);
        jdbc.update("update produto set quantidade_estoque = 3 where id = ?", novo);
        assertThat(jdbc.queryForObject("select controla_lote from produto where id = ?", Boolean.class, novo)).isFalse();

        // Reprocessar a inicialização: nada a aplicar, checksums válidos, histórico preservado.
        var segundaRodada = flyway("11").migrate();
        assertThat(segundaRodada.migrationsExecuted).isZero();
        flyway("11").validate();
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success = 0", Integer.class)).isZero();
        assertThat(jdbc.queryForList("select version from flyway_schema_history where version is not null order by installed_rank", String.class))
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11");

        // V12 (vacina ligada ao estoque): sobe por cima da V11, aditiva, sem alterar nenhum dado e sem vínculo nenhum.
        List<Map<String, Object>> produtosV11 = jdbc.queryForList("select * from produto order by id");
        List<Map<String, Object>> vacinasV11 = jdbc.queryForList("select id, especie_id, nome, ativo from vacina order by id");
        flyway(null).migrate();
        assertThat(versaoAplicada()).isEqualTo("12");
        assertThat(jdbc.queryForList("select * from produto order by id")).isEqualTo(produtosV11);
        assertThat(jdbc.queryForList("select id, especie_id, nome, ativo from vacina order by id")).isEqualTo(vacinasV11);
        assertThat(jdbc.queryForObject("select count(*) from vacina where produto_id is not null", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from carteira_vacina where lote_id is not null", Integer.class)).isZero();
        assertThat(flyway(null).migrate().migrationsExecuted).isZero();
        flyway(null).validate();
    }

    private static String versaoAplicada() {
        return jdbc.queryForObject("select version from flyway_schema_history where success = 1 and version is not null "
                + "order by installed_rank desc limit 1", String.class);
    }

    private static Flyway flyway(String alvo) {
        var config = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration");
        if (alvo != null) {
            config.target(alvo);
        }
        return config.load();
    }

    /** Estado típico de um banco em produção na V10: produtos com e sem saldo, compra recebida, venda, ajuste. */
    private static void semear() {
        jdbc.update("insert into usuario (username, email, senha_hash, papel_id) values ('legado', 'legado@teste.local', 'x', (select min(id) from papel))");
        long usuario = jdbc.queryForObject("select id from usuario where username = 'legado'", Long.class);
        jdbc.update("insert into fornecedor (nome_fantasia, cnpj) values ('Fornecedor Legado', '11222333000181')");
        long fornecedor = jdbc.queryForObject("select id from fornecedor", Long.class);
        long categoria = jdbc.queryForObject("select min(id) from categoria_produto", Long.class);
        jdbc.update("insert into produto (nome, categoria_id, preco_custo, preco_venda, quantidade_estoque, estoque_minimo) values ('Ração 10kg', ?, 80.00, 129.90, 7, 2)", categoria);
        jdbc.update("insert into produto (nome, categoria_id, preco_custo, preco_venda, quantidade_estoque, estoque_minimo) values ('Brinquedo', ?, 5.00, 12.00, 0, 5)", categoria);
        long racao = jdbc.queryForObject("select id from produto where nome = 'Ração 10kg'", Long.class);
        jdbc.update("insert into compra (fornecedor_id, data_compra, valor_total, status) values (?, curdate(), 1000.00, 'RECEBIDO')", fornecedor);
        long compra = jdbc.queryForObject("select max(id) from compra", Long.class);
        jdbc.update("insert into item_compra (compra_id, produto_id, quantidade, preco_custo_unitario) values (?, ?, 10, 80.00)", compra, racao);
        jdbc.update("insert into venda (valor_total, forma_pagamento, usuario_id) values (259.80, 'PIX', ?)", usuario);
        long venda = jdbc.queryForObject("select max(id) from venda", Long.class);
        jdbc.update("insert into item_venda (venda_id, produto_id, quantidade, preco_unitario) values (?, ?, 2, 129.90)", venda, racao);
        jdbc.update("insert into movimentacao_estoque (produto_id, tipo, quantidade, saldo_anterior, saldo_resultante, compra_id, usuario_id) values (?, 'ENTRADA_COMPRA', 10, 0, 10, ?, ?)", racao, compra, usuario);
        jdbc.update("insert into movimentacao_estoque (produto_id, tipo, quantidade, saldo_anterior, saldo_resultante, venda_id, usuario_id) values (?, 'SAIDA_VENDA', -2, 10, 8, ?, ?)", racao, venda, usuario);
        jdbc.update("insert into movimentacao_estoque (produto_id, tipo, quantidade, saldo_anterior, saldo_resultante, usuario_id, motivo) values (?, 'AJUSTE', -1, 8, 7, ?, 'contagem física')", racao, usuario);
    }
}
