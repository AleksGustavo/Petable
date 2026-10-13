package br.com.tecnodreamer.petconnect.financeiro.web;

import br.com.tecnodreamer.petconnect.financeiro.application.CaixaService;
import br.com.tecnodreamer.petconnect.financeiro.application.FinanceiroService;
import br.com.tecnodreamer.petconnect.shared.error.ConflictException;
import br.com.tecnodreamer.petconnect.venda.application.RegistroVendaIdempotente;
import br.com.tecnodreamer.petconnect.venda.application.ResultadoVenda;
import br.com.tecnodreamer.petconnect.venda.domain.FormaPagamento;
import br.com.tecnodreamer.petconnect.venda.web.VendaRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class FinanceiroConcorrenciaIT extends FinanceiroIntegrationTest {

    @Autowired CaixaService caixas;
    @Autowired FinanceiroService financeiro;
    @Autowired RegistroVendaIdempotente registro;

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

    private long novoUsuario(String papel) throws Exception {
        return criarUsuario(nomeUsuario(), papel);
    }

    private static Map<FormaPagamento, BigDecimal> zerado() {
        Map<FormaPagamento, BigDecimal> m = new EnumMap<>(FormaPagamento.class);
        for (FormaPagamento f : FormaPagamento.values()) {
            m.put(f, BigDecimal.ZERO);
        }
        return m;
    }

    @Test
    void doisFechamentosSimultaneosSoUmPassa() throws Exception {
        long uid = novoUsuario("VENDAS");
        caixas.abrir(uid, BigDecimal.ZERO);

        List<Object> r = emParalelo(List.of(() -> caixas.fechar(uid, zerado(), null), () -> caixas.fechar(uid, zerado(), null)));

        assertThat(r).filteredOn(ConflictException.class::isInstance).hasSize(1);
        assertThat(r).filteredOn(o -> !(o instanceof Throwable)).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from caixa where usuario_id = ? and status = 'FECHADO'", Integer.class, uid))
                .isEqualTo(1);
    }

    @Test
    void doisPagamentosSimultaneosSoUmPassa() throws Exception {
        long conta = compraRecebida("1:30.00");
        long adminId = adminId();
        admin();

        List<Object> r = emParalelo(List.of(() -> financeiro.pagar(conta, FormaPagamento.PIX, adminId),
                () -> financeiro.pagar(conta, FormaPagamento.PIX, adminId)));

        assertThat(r).filteredOn(ConflictException.class::isInstance).hasSize(1);
        assertThat(lancamentos("conta_pagar_id = ?", conta)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from conta_pagar where id = ?", String.class, conta)).isEqualTo("PAGA");
    }

    @Test
    void vendasSimultaneasNaoPerdemLancamentos() throws Exception {
        long p = produto("10.00", 20);
        long uid = novoUsuario("VENDAS");
        caixas.abrir(uid, BigDecimal.ZERO);
        List<Callable<Object>> tarefas = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tarefas.add(() -> registro.registrar(new VendaRequest(null, List.of(new VendaRequest.Item(p, 1)), null,
                    FormaPagamento.DINHEIRO, new BigDecimal("10.00"), null), UUID.randomUUID().toString(), uid));
        }

        List<Object> r = emParalelo(tarefas);

        assertThat(r).allMatch(ResultadoVenda.class::isInstance);
        assertThat(saldo(p)).isEqualTo(15);
        Long caixa = jdbc.queryForObject("select id from caixa where usuario_id = ? and status = 'ABERTO'", Long.class, uid);
        assertThat(lancamentos("caixa_id = ? and categoria = 'VENDA_PRODUTO'", caixa)).isEqualTo(5);
        assertThat(caixas.previaFechamento(uid).esperado().get(FormaPagamento.DINHEIRO)).isEqualByComparingTo("50.00");
    }

    @Test
    void duasAberturasSimultaneasGeramUmCaixa() throws Exception {
        long uid = novoUsuario("SERVICO");

        List<Object> r = emParalelo(List.of(() -> caixas.abrir(uid, BigDecimal.ZERO), () -> caixas.abrir(uid, BigDecimal.TEN)));

        assertThat(r).filteredOn(ConflictException.class::isInstance).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from caixa where usuario_id = ? and status = 'ABERTO'", Integer.class, uid))
                .isEqualTo(1);
    }
}
