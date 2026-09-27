package br.com.tecnodreamer.petconnect.financeiro.application;

import br.com.tecnodreamer.petconnect.financeiro.web.LancamentoManualRequest;
import br.com.tecnodreamer.petconnect.financeiro.web.LancamentoResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** Fecha a corrida da idempotência do lançamento manual (mesmo padrão da venda). Fora da transação de propósito. */
@Component
public class LancamentoManualIdempotente {

    private final FinanceiroService financeiro;

    public LancamentoManualIdempotente(FinanceiroService financeiro) {
        this.financeiro = financeiro;
    }

    public ResultadoLancamento lancar(LancamentoManualRequest req, String chave, Long usuarioId) {
        try {
            return financeiro.lancarManual(req, chave, usuarioId);
        } catch (DataIntegrityViolationException e) {
            String chaveOrigem = "MANUAL:" + FinanceiroService.chaveValida(chave);
            return financeiro.porChave(chaveOrigem)
                    .map(l -> new ResultadoLancamento(LancamentoResponse.of(FinanceiroService.doMesmoUsuario(l, usuarioId)), true))
                    .orElseThrow(() -> e);
        }
    }
}
