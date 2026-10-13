package br.com.tecnodreamer.petconnect.financeiro.application;

import br.com.tecnodreamer.petconnect.agendamento.infrastructure.ServicoRealizadoRepository;
import br.com.tecnodreamer.petconnect.compra.domain.Compra;
import br.com.tecnodreamer.petconnect.compra.infrastructure.CompraRepository;
import br.com.tecnodreamer.petconnect.financeiro.infrastructure.ContaPagarRepository;
import br.com.tecnodreamer.petconnect.financeiro.infrastructure.LancamentoRepository;
import br.com.tecnodreamer.petconnect.venda.domain.FormaPagamento;
import br.com.tecnodreamer.petconnect.venda.infrastructure.VendaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backfill do histórico anterior à V8 (DA-18). Idempotente por chave_origem /
 * compra_id: rodar de novo não duplica nada. Não é endpoint: só roda pelo
 * {@link FinanceiroBackfillRunner} (petconnect.financeiro.backfill=true) ou em teste.
 * Serviços antigos NÃO têm forma de pagamento: ficam pendentes, a menos que
 * petconnect.financeiro.backfill-servico-forma seja configurado explicitamente.
 */
@Service
public class FinanceiroBackfill {

    private static final Logger log = LoggerFactory.getLogger(FinanceiroBackfill.class);

    public record Resultado(int vendas, int estornos, int contas, int servicos, int servicosPendentes) {
    }

    private final LancamentoRepository lancamentos;
    private final ContaPagarRepository contas;
    private final VendaRepository vendas;
    private final CompraRepository compras;
    private final ServicoRealizadoRepository servicos;
    private final FinanceiroService financeiro;
    private final FormaPagamento formaServico;

    public FinanceiroBackfill(LancamentoRepository lancamentos, ContaPagarRepository contas, VendaRepository vendas,
            CompraRepository compras, ServicoRealizadoRepository servicos, FinanceiroService financeiro,
            @Value("${petconnect.financeiro.backfill-servico-forma:#{null}}") FormaPagamento formaServico) {
        this.lancamentos = lancamentos;
        this.contas = contas;
        this.vendas = vendas;
        this.compras = compras;
        this.servicos = servicos;
        this.financeiro = financeiro;
        this.formaServico = formaServico;
    }

    @Transactional
    public Resultado executar() {
        return executar(formaServico);
    }

    /** Mesmo backfill, com a forma dos serviços antigos vinda de quem chama (migração do legado). */
    @Transactional
    public Resultado executar(FormaPagamento forma) {
        int nVendas = 0;
        for (Long id : lancamentos.vendasSemLancamento()) {
            financeiro.registrarVendaHistorica(vendas.findById(id).orElseThrow());
            nVendas++;
        }
        int nEstornos = 0;
        for (Long id : lancamentos.vendasCanceladasSemEstorno()) {
            if (financeiro.registrarEstornoHistorico(vendas.findById(id).orElseThrow()).isPresent()) {
                nEstornos++;
            }
        }
        int nContas = 0;
        for (Long id : contas.comprasRecebidasSemConta()) {
            Compra c = compras.findById(id).orElseThrow();
            financeiro.criarContaPagar(c, c.getDataEntrega() != null ? c.getDataEntrega() : c.getDataCompra());
            nContas++;
        }
        int nServicos = 0;
        int pendentes = 0;
        for (Long id : lancamentos.servicosSemLancamento()) {
            if (forma == null) {
                pendentes++;
            } else {
                financeiro.registrarServicoHistorico(servicos.findById(id).orElseThrow(), forma);
                nServicos++;
            }
        }
        Resultado r = new Resultado(nVendas, nEstornos, nContas, nServicos, pendentes);
        log.info("Backfill financeiro: {}", r);
        return r;
    }
}
