package br.com.tecnodreamer.petconnect.venda.web;

import br.com.tecnodreamer.petconnect.produto.infrastructure.LoteEstoqueRepository;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Apoio dos ITs de lote (etapa 03): monta produto controlado por HTTP (ajuste de saldo legado + regularização) e, depois de
 * cada teste, confere I2 e I3 para todo produto que o teste criou.
 */
public abstract class LoteIntegrationTest extends VendaIntegrationTest {

    @Autowired
    protected LoteEstoqueRepository lotesRepo;

    private final List<Long> criados = new ArrayList<>();

    @AfterEach
    void conferirReconciliacao() {
        if (criados.isEmpty()) {
            return;
        }
        assertThat(lotesRepo.divergenciasDoProdutoControlado()).doesNotContainAnyElementsOf(criados);
        for (Long produtoId : criados) {
            assertThat(jdbc.queryForList("""
                    select l.id from lote_estoque l where l.produto_id = ?
                      and l.saldo <> coalesce((select sum(m.quantidade) from movimentacao_estoque m where m.lote_id = l.id), 0)""",
                    Long.class, produtoId)).as("I2 do produto " + produtoId).isEmpty();
            // I1: total do produto = soma dos movimentos, exceto REGULARIZACAO_LOTE.
            assertThat(jdbc.queryForObject("""
                    select coalesce(sum(quantidade), 0) from movimentacao_estoque where produto_id = ? and tipo <> 'REGULARIZACAO_LOTE'""",
                    Integer.class, produtoId)).as("I1 do produto " + produtoId).isEqualTo(saldo(produtoId));
        }
    }

    /**
     * Produto com controle de lote. {@code especificacoes}: "CODIGO:dias:quantidade", onde dias é o prazo de validade a partir
     * de hoje (negativo = já vencido; "-" = sem validade). O saldo legado é a soma das quantidades.
     */
    protected long produtoControlado(String preco, String... especificacoes) throws Exception {
        int total = 0;
        for (String e : especificacoes) {
            total += Integer.parseInt(e.split(":")[2]);
        }
        long id = produto(preco, total);
        criados.add(id);
        ativarControle(id, false, null, especificacoes).andExpect(status().isCreated());
        return id;
    }

    protected ResultActions ativarControle(long produtoId, boolean permiteSemValidade, Integer margem, String... especificacoes)
            throws Exception {
        return mvc.perform(post("/api/v1/produtos/" + produtoId + "/controle-lote").with(bearer(admin()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permiteSemValidade\": %s, \"diasBloqueioValidade\": %s, \"lotes\": %s}".formatted(permiteSemValidade,
                        margem == null ? "null" : margem, lotesJson(especificacoes))));
    }

    protected static String lotesJson(String... especificacoes) {
        List<String> itens = new ArrayList<>();
        for (String e : especificacoes) {
            String[] p = e.split(":");
            String validade = p[1].equals("-") ? "null" : "\"" + HOJE.plusDays(Long.parseLong(p[1])) + "\"";
            itens.add("{\"codigoLote\": \"%s\", \"validade\": %s, \"quantidade\": %s}".formatted(p[0], validade, p[2]));
        }
        return "[" + String.join(",", itens) + "]";
    }

    protected long loteId(long produtoId, String codigo) {
        return jdbc.queryForObject("select id from lote_estoque where produto_id = ? and codigo_lote = ?", Long.class, produtoId, codigo);
    }

    protected int saldoLote(long produtoId, String codigo) {
        return jdbc.queryForObject("select saldo from lote_estoque where produto_id = ? and codigo_lote = ?", Integer.class, produtoId, codigo);
    }

    protected int totalLotes(long produtoId) {
        return jdbc.queryForObject("select coalesce(sum(saldo), 0) from lote_estoque where produto_id = ?", Integer.class, produtoId);
    }

    protected int movimentosDoLote(long produtoId, String codigo, String tipo) {
        return jdbc.queryForObject("""
                select count(*) from movimentacao_estoque m join lote_estoque l on l.id = m.lote_id
                where m.produto_id = ? and l.codigo_lote = ? and m.tipo = ?""", Integer.class, produtoId, codigo, tipo);
    }

    protected ResultActions receberComLotes(long compraId, long produtoId, String... especificacoes) throws Exception {
        return mvc.perform(post("/api/v1/compras/" + compraId + "/receber").with(bearer(admin()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dataEntrega\": \"%s\", \"dataVencimento\": \"%s\", \"lotes\": [{\"produtoId\": %d, \"lotes\": %s}]}"
                        .formatted(HOJE, HOJE.plusDays(30), produtoId, lotesJson(especificacoes))));
    }

    protected ResultActions cancelarVenda(String token, long vendaId, String destino) throws Exception {
        String corpo = destino == null ? "{\"motivo\": \"Cancelamento de teste\"}"
                : "{\"motivo\": \"Cancelamento de teste\", \"destinoEstoque\": \"" + destino + "\"}";
        return mvc.perform(post(URL + "/" + vendaId + "/cancelar").with(bearer(token)).contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    protected ResultActions lotes(long produtoId) throws Exception {
        return mvc.perform(get("/api/v1/produtos/" + produtoId + "/lotes").with(bearer(admin())));
    }

    protected ResultActions postLote(String caminho, String json) throws Exception {
        return mvc.perform(post(caminho).with(bearer(admin())).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected static String chave() {
        return UUID.randomUUID().toString();
    }

    protected static LocalDate hoje() {
        return HOJE;
    }

    protected void acompanhar(long produtoId) {
        criados.add(produtoId);
    }
}
