package br.com.tecnodreamer.petconnect.produto.web;

import br.com.tecnodreamer.petconnect.produto.domain.Produto;

import java.math.BigDecimal;

/**
 * Busca do PDV. {@code quantidadeVendavel} é o que o balcão pode vender agora: igual ao saldo para produto sem lote; para
 * produto com lote, só os lotes não vencidos, liberados e fora da margem (D4). {@code disponivel} segue o vendável.
 */
public record ProdutoRapidoResponse(Long id, String nome, String codigoBarras, BigDecimal precoVenda,
        int quantidadeEstoque, boolean disponivel, int quantidadeVendavel, boolean controlaLote) {

    public static ProdutoRapidoResponse of(Produto p, int vendavel) {
        return new ProdutoRapidoResponse(p.getId(), p.getNome(), p.getCodigoBarras(), p.getPrecoVenda(),
                p.getQuantidadeEstoque(), vendavel > 0, vendavel, p.isControlaLote());
    }

    public static ProdutoRapidoResponse of(Produto p) {
        return of(p, p.getQuantidadeEstoque());
    }
}
