package br.com.tecnodreamer.petconnect.produto.application;

import br.com.tecnodreamer.petconnect.produto.domain.LoteEstoque;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Distribui uma quantidade pelos lotes já ordenados em FEFO (D6). Função pura e única: a prévia do PDV e a
 * venda chamam esta mesma lógica, então nunca prometem um lote e vendem outro (D8).
 */
public final class AlocadorFefo {

    public record Alocacao(LoteEstoque lote, int quantidade) {
    }

    private AlocadorFefo() {
    }

    /** Vazio quando os lotes vendáveis não cobrem a quantidade. Não altera nenhum lote. */
    public static Optional<List<Alocacao>> alocar(List<LoteEstoque> vendaveisEmOrdemFefo, int quantidade) {
        if (quantidade <= 0) {
            throw new IllegalArgumentException("A quantidade a alocar deve ser positiva.");
        }
        List<Alocacao> alocacoes = new ArrayList<>();
        int restante = quantidade;
        for (LoteEstoque lote : vendaveisEmOrdemFefo) {
            if (restante == 0) {
                break;
            }
            int usar = Math.min(lote.getSaldo(), restante);
            if (usar > 0) {
                alocacoes.add(new Alocacao(lote, usar));
                restante -= usar;
            }
        }
        return restante == 0 ? Optional.of(alocacoes) : Optional.empty();
    }
}
