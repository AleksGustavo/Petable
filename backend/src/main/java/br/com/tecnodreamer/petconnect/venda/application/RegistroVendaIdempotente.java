package br.com.tecnodreamer.petconnect.venda.application;

import br.com.tecnodreamer.petconnect.venda.web.VendaRequest;
import br.com.tecnodreamer.petconnect.venda.web.VendaResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Fecha a corrida da idempotência (DA-06): duas requisições com a mesma chave
 * podem passar juntas pela checagem inicial; a UNIQUE de {@code chave_idempotencia}
 * derruba a segunda gravação (rollback completo, sem baixa), e aqui ela recebe a
 * venda já gravada pela primeira. Fica fora da transação de propósito.
 */
@Component
public class RegistroVendaIdempotente {

    private final VendaService vendas;

    public RegistroVendaIdempotente(VendaService vendas) {
        this.vendas = vendas;
    }

    public ResultadoVenda registrar(VendaRequest req, String chave, Long usuarioId) {
        try {
            return vendas.registrar(req, chave, usuarioId);
        } catch (DataIntegrityViolationException e) {
            String valida = VendaService.chave(chave);
            return vendas.porChave(valida)
                    .map(v -> new ResultadoVenda(vendas.buscar(VendaService.daMesmaPessoa(v, usuarioId).getId()), true))
                    .orElseThrow(() -> e);
        }
    }
}
