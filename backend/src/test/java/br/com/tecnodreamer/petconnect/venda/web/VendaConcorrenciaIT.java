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

/** Concorrência real contra o MySQL: locks em ordem de id e UNIQUE da chave de idempotência. */
class VendaConcorrenciaIT extends VendaIntegrationTest {

    @Autowired
    RegistroVendaIdempotente registro;

    @Autowired
    CompraService compras;

    private static VendaRequest pedido(long produtoId, int quantidade) {
        return new VendaRequest(null, List.of(new VendaRequest.Item(produtoId, quantidade)), null, FormaPagamento.PIX, null, null);
    }

    /** Dispara as tarefas juntas e devolve o resultado ou a exceção de cada uma. */
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
                    resultados.add(f.get(60, TimeUnit.SECONDS));
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
    void duasVendasDoUltimoItemSoUmaPassa() throws Exception {
        long p = produto("10.00", 1);
        long adminId = adminId();
        admin(); // garante o caixa aberto do ADMIN (etapa 06)

        List<Object> r = emParalelo(List.of(
                () -> registro.registrar(pedido(p, 1), UUID.randomUUID().toString(), adminId),
                () -> registro.registrar(pedido(p, 1), UUID.randomUUID().toString(), adminId)));

        assertThat(r).filteredOn(ResultadoVenda.class::isInstance).hasSize(1);
        assertThat(r).filteredOn(EstoqueInsuficienteException.class::isInstance).hasSize(1);
        assertThat(saldo(p)).isZero();
        assertThat(movimentacoes(p, "SAIDA_VENDA")).isEqualTo(1);
    }

    @Test
    void vendaERecebimentoSimultaneosNaoPerdemAtualizacao() throws Exception {
        long p = produto("10.00", 20);
        long fornecedor = criarFornecedor(unico("Fornecedor Concorrência"));
        long compra = criarCompra(fornecedor, String.valueOf(p), "7:5.00");
        long adminId = adminId();
        admin(); // garante o caixa aberto do ADMIN (etapa 06)

        List<Object> r = emParalelo(List.of(
                () -> registro.registrar(pedido(p, 3), UUID.randomUUID().toString(), adminId),
                () -> compras.receber(compra, HOJE, HOJE.plusDays(30), adminId)));

        assertThat(r).noneMatch(Throwable.class::isInstance);
        assertThat(saldo(p)).isEqualTo(20 + 7 - 3);
        Integer ultimo = jdbc.queryForObject(
                "select saldo_resultante from movimentacao_estoque where produto_id = ? order by id desc limit 1", Integer.class, p);
        assertThat(ultimo).isEqualTo(24);
    }

    @Test
    void mesmaChaveEmParaleloGeraUmaVenda() throws Exception {
        long p = produto("10.00", 10);
        long adminId = adminId();
        admin(); // garante o caixa aberto do ADMIN (etapa 06)
        String chave = UUID.randomUUID().toString();

        List<Object> r = emParalelo(List.of(
                () -> registro.registrar(pedido(p, 2), chave, adminId),
                () -> registro.registrar(pedido(p, 2), chave, adminId)));

        assertThat(r).allMatch(ResultadoVenda.class::isInstance);
        assertThat(r).extracting(o -> ((ResultadoVenda) o).venda().id()).containsOnly(((ResultadoVenda) r.getFirst()).venda().id());
        assertThat(r).filteredOn(o -> ((ResultadoVenda) o).repetida()).hasSize(1);
        assertThat(saldo(p)).isEqualTo(8);
        assertThat(jdbc.queryForObject("select count(*) from venda where chave_idempotencia = ?", Integer.class, chave)).isEqualTo(1);
    }
}
