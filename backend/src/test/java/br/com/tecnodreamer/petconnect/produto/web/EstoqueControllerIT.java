package br.com.tecnodreamer.petconnect.produto.web;

import br.com.tecnodreamer.petconnect.produto.application.EstoqueService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EstoqueControllerIT extends EstoqueIntegrationTest {

    @Autowired
    EstoqueService estoqueService;

    private String url(long id) {
        return "/api/v1/produtos/" + id + "/ajustes-estoque";
    }

    @Test
    void adminDeveAjustarEstoque() throws Exception {
        long id = criarProduto(unico("Ajuste Admin"), null, "10.00", 1);

        ajustar(id, 10, "Contagem inicial")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("AJUSTE"))
                .andExpect(jsonPath("$.saldoAnterior").value(0))
                .andExpect(jsonPath("$.saldoResultante").value(10))
                .andExpect(jsonPath("$.usuario.nome").value(ADMIN_USER));
        assertThat(acoesAuditadas("produto", id)).contains("ESTOQUE_AJUSTADO");
    }

    @Test
    void vendasNaoDeveAjustarEstoque() throws Exception {
        long id = criarProduto(unico("Ajuste Vendas"), null, "10.00", 1);
        mvc.perform(post(url(id)).with(comPapel("VENDAS")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantidade\": 1, \"motivo\": \"Tentativa\"}")).andExpect(status().isForbidden());
    }

    @Test
    void servicoNaoDeveAjustarEstoque() throws Exception {
        long id = criarProduto(unico("Ajuste Servico"), null, "10.00", 1);
        mvc.perform(post(url(id)).with(comPapel("SERVICO")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ajusteDeveAtualizarSaldo() throws Exception {
        long id = criarProduto(unico("Ajuste Saldo"), null, "10.00", 1);
        ajustar(id, 10, "Contagem inicial").andExpect(status().isCreated());
        ajustar(id, -3, "Três avariadas").andExpect(status().isCreated());

        assertThat(saldo(id)).isEqualTo(7);
        assertThat(custo(id)).isEqualTo("10.00");
    }

    @Test
    void ajusteDeveCriarMovimentacao() throws Exception {
        long id = criarProduto(unico("Ajuste Movimentação"), null, "10.00", 1);
        ajustar(id, 2, "Entrada de teste").andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("select count(*) from movimentacao_estoque where produto_id = ? and tipo = 'AJUSTE'",
                Integer.class, id)).isEqualTo(1);
        ajustar(id, 0, "Zero não pode").andExpect(status().isBadRequest());
        ajustar(id, 1, "curt").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.motivo").exists());
    }

    @Test
    void ajusteNegativoAlemDoSaldoDeveRetornar409() throws Exception {
        long id = criarProduto(unico("Ajuste Negativo"), null, "10.00", 1);
        ajustar(id, 2, "Saldo inicial").andExpect(status().isCreated());

        ajustar(id, -3, "Baixa maior que saldo")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("O ajuste resultaria em estoque negativo."));
        assertThat(saldo(id)).isEqualTo(2);
    }

    @Test
    void deveListarHistoricoDoProduto() throws Exception {
        long id = criarProduto(unico("Histórico"), null, "10.00", 1);
        ajustar(id, 5, "Primeira entrada").andExpect(status().isCreated());
        ajustar(id, -1, "Segunda saída").andExpect(status().isCreated());

        mvc.perform(get("/api/v1/produtos/" + id + "/movimentacoes").with(comPapel("SERVICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].motivo").value("Segunda saída"))
                .andExpect(jsonPath("$.content[1].motivo").value("Primeira entrada"));
        mvc.perform(get("/api/v1/produtos/" + id + "/movimentacoes").param("tipo", "ENTRADA_COMPRA").with(comPapel("VENDAS")))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    /** Dois fluxos ajustando o MESMO produto ao mesmo tempo: o lock pessimista impede "lost update". */
    @Test
    void doisAjustesConcorrentesNaoDevemPerderAtualizacao() throws Exception {
        long id = criarProduto(unico("Concorrência"), null, "10.00", 1);
        long adminId = jdbc.queryForObject("select id from usuario where username = ?", Long.class, ADMIN_USER);
        estoqueService.ajustar(id, 50, "Saldo inicial", adminId);
        int rodadas = 15;

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            Future<?> entradas = pool.submit(() -> {
                largada.await();
                for (int i = 0; i < rodadas; i++) {
                    estoqueService.ajustar(id, 2, "Entrada concorrente", adminId);
                }
                return null;
            });
            Future<?> saidas = pool.submit(() -> {
                largada.await();
                for (int i = 0; i < rodadas; i++) {
                    estoqueService.ajustar(id, -1, "Saída concorrente", adminId);
                }
                return null;
            });
            largada.countDown();
            entradas.get(60, TimeUnit.SECONDS);
            saidas.get(60, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        // saldoFinal = saldoInicial + soma dos deltas aceitos = 50 + 15×2 − 15×1.
        assertThat(saldo(id)).isEqualTo(50 + rodadas * 2 - rodadas);
        assertThat(jdbc.queryForObject("select count(*) from movimentacao_estoque where produto_id = ?", Integer.class, id))
                .isEqualTo(1 + 2 * rodadas);
        Integer ultimoSaldo = jdbc.queryForObject(
                "select saldo_resultante from movimentacao_estoque where produto_id = ? order by id desc limit 1", Integer.class, id);
        assertThat(ultimoSaldo).isEqualTo(saldo(id));
    }
}
