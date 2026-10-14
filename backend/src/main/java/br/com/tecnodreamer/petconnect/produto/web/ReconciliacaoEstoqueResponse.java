package br.com.tecnodreamer.petconnect.produto.web;

import java.time.Instant;
import java.util.List;

/**
 * Resultado da reconciliação do estoque (lotes, etapa 05). {@code consistente} só é verdadeiro sem nenhuma divergência.
 * {@code truncado} indica que alguma verificação passou do limite de itens listados (o que está na lista continua válido).
 */
public record ReconciliacaoEstoqueResponse(boolean consistente, boolean truncado, Instant executadoEm, long produtos,
        long produtosControlados, long lotes, List<Divergencia> divergencias) {

    /**
     * @param regra       I1 total do produto = soma dos movimentos; I2 saldo do lote = soma dos movimentos do lote; I3 total do produto
     *                    controlado = soma dos lotes; I5 alocação da venda soma a quantidade do item; I7 movimento sem lote depois que o
     *                    produto passou a ter lote
     * @param esperado    valor que a regra exige
     * @param encontrado  valor gravado
     */
    public record Divergencia(String regra, String descricao, Long produtoId, Long loteId, Long referenciaId, long esperado,
            long encontrado) {
    }
}
