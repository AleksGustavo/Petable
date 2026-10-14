package br.com.tecnodreamer.petconnect.venda.web;

import br.com.tecnodreamer.petconnect.compra.application.CompraService;
import br.com.tecnodreamer.petconnect.produto.domain.EstoqueInsuficienteException;
import br.com.tecnodreamer.petconnect.venda.application.RegistroVendaIdempotente;
import br.com.tecnodreamer.petconnect.venda.application.ResultadoVenda;
import br.com.tecnodreamer.petconnect.venda.domain.FormaPagamento;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concorrência real contra o MySQL com produto controlado por lote: a linha do produto travada é o mutex dos lotes (D15),
 * então vendas simultâneas nunca passam do saldo vendável e a repetição de uma chave baixa uma vez só.
 */
class LoteConcorrenciaIT extends LoteIntegrationTest {

    @Autowired
    RegistroVendaIdempotente registro;
    @Autowired
    CompraService compras;

    private static VendaRequest pedido(long produtoId, int quantidade) {
        return new VendaRequest(null, List.of(new VendaRequest.Item(produtoId, quantidade)), null, FormaPagamento.PIX, null, null);
    }

    private static List<Object> emParalelo(List<Callable<Object>> tarefas) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tarefas.size());
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<Object>> futuros = new ArrayList<>();
            for (Callable<Object> t : tarefas) {
                futuros.add(pool.submit(() -> {
                    largada.await();
                    return t.call();
                }));
            }
            largada.countDown();
            List<Object> resultados = new ArrayList<>();
            for (Future<Object> f : futuros) {
                try {
                    resultados.add(f.get(90, TimeUnit.SECONDS));
                } catch (ExecutionException e) {
                    resultados.add(e.getCause());
                }
            }
            return resultados;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void doisCaixasVendemOUltimoItemDoLoteSoUmaConfirma() throws Exception {
        long p = produtoControlado("10.00", "UNICO:30:1");
        long adminId = adminId();
        admin();

        List<Object> r = emParalelo(List.of(
                () -> registro.registrar(pedido(p, 1), UUID.randomUUID().toString(), adminId),
                () -> registro.registrar(pedido(p, 1), UUID.randomUUID().toString(), adminId)));

        assertThat(r).filteredOn(ResultadoVenda.class::isInstance).hasSize(1);
        assertThat(r).as(r.toString()).filteredOn(EstoqueInsuficienteException.class::isInstance).hasSize(1);
        assertThat(saldoLote(p, "UNICO")).isZero();
        assertThat(saldo(p)).isZero();
        assertThat(movimentacoes(p, "SAIDA_VENDA")).isEqualTo(1);
    }

    @Test
    void oitoVendasSimultaneasSobreCincoUnidadesConfirmamExatamenteCinco() throws Exception {
        long p = produtoControlado("10.00", "A:30:2", "B:60:3");
        long adminId = adminId();
        admin();

        List<Callable<Object>> tarefas = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tarefas.add(() -> registro.registrar(pedido(p, 1), UUID.randomUUID().toString(), adminId));
        }
        List<Object> r = emParalelo(tarefas);

        assertThat(r).filteredOn(ResultadoVenda.class::isInstance).hasSize(5);
        assertThat(r).as(r.toString()).filteredOn(EstoqueInsuficienteException.class::isInstance).hasSize(3);
        assertThat(saldoLote(p, "A")).isZero();
        assertThat(saldoLote(p, "B")).isZero();
        assertThat(saldo(p)).isZero();
        assertThat(movimentacoes(p, "SAIDA_VENDA")).isEqualTo(5);
        // Cada unidade saiu de um lote só uma vez: a soma alocada é o que foi vendido.
        assertThat(jdbc.queryForObject("select coalesce(sum(il.quantidade), 0) from item_venda_lote il join lote_estoque l on l.id = il.lote_id where l.produto_id = ?",
                Integer.class, p)).isEqualTo(5);
    }

    @Test
    void mesmaChaveEmParaleloBaixaOLoteUmaVezSo() throws Exception {
        long p = produtoControlado("10.00", "A:30:10");
        long adminId = adminId();
        admin();
        String chave = UUID.randomUUID().toString();

        List<Object> r = emParalelo(List.of(
                () -> registro.registrar(pedido(p, 4), chave, adminId),
                () -> registro.registrar(pedido(p, 4), chave, adminId),
                () -> registro.registrar(pedido(p, 4), chave, adminId)));

        assertThat(r).allMatch(ResultadoVenda.class::isInstance);
        assertThat(r).extracting(o -> ((ResultadoVenda) o).venda().id()).containsOnly(((ResultadoVenda) r.getFirst()).venda().id());
        assertThat(saldoLote(p, "A")).isEqualTo(6);
        assertThat(saldo(p)).isEqualTo(6);
        assertThat(jdbc.queryForObject("select count(*) from venda where chave_idempotencia = ?", Integer.class, chave)).isEqualTo(1);
        assertThat(movimentacoes(p, "SAIDA_VENDA")).isEqualTo(1);
    }

    @Test
    void vendaERecebimentoSimultaneosNaoPerdemAtualizacaoDoLote() throws Exception {
        long p = produtoControlado("10.00", "A:30:20");
        long fornecedor = criarFornecedor(unico("Fornecedor Concorrência Lote"));
        long compra = criarCompra(fornecedor, String.valueOf(p), "7:5.00");
        long adminId = adminId();
        admin();

        List<Object> r = emParalelo(List.of(
                () -> registro.registrar(pedido(p, 3), UUID.randomUUID().toString(), adminId),
                () -> compras.receber(compra, hoje(), hoje().plusDays(30),
                        List.of(new br.com.tecnodreamer.petconnect.compra.web.RecebimentoRequest.LotesDoItem(p,
                                List.of(new br.com.tecnodreamer.petconnect.produto.application.LoteInformado("B", hoje().plusDays(90), 7)))),
                        adminId)));

        assertThat(r).noneMatch(Throwable.class::isInstance);
        assertThat(saldo(p)).isEqualTo(20 + 7 - 3);
        assertThat(saldoLote(p, "A")).isEqualTo(17);
        assertThat(saldoLote(p, "B")).isEqualTo(7);
    }

    @Test
    void vendaECancelamentoSimultaneosMantemOsLotesCoerentes() throws Exception {
        long p = produtoControlado("10.00", "A:30:6");
        long adminId = adminId();
        admin();
        long existente = registro.registrar(pedido(p, 4), UUID.randomUUID().toString(), adminId).venda().id();

        List<Object> r = emParalelo(List.of(
                () -> registro.registrar(pedido(p, 5), UUID.randomUUID().toString(), adminId),
                () -> cancelarPorServico(existente, adminId)));

        // Qualquer que seja a ordem, o saldo final bate com o livro-razão (conferido no @AfterEach) e nunca fica negativo.
        assertThat(saldoLote(p, "A")).isBetween(0, 6);
        assertThat(saldo(p)).isEqualTo(saldoLote(p, "A"));
        assertThat(r).hasSize(2);
    }

    @Autowired
    br.com.tecnodreamer.petconnect.venda.application.VendaService vendaService;

    private Object cancelarPorServico(long vendaId, long usuarioId) {
        return vendaService.cancelar(vendaId, "Cancelamento concorrente", br.com.tecnodreamer.petconnect.venda.domain.DestinoEstoque.RETORNAR, usuarioId);
    }
}
