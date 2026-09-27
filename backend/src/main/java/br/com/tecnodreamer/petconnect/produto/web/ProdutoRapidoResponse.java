package br.com.tecnodreamer.petconnect.produto.web;

import br.com.tecnodreamer.petconnect.produto.domain.Produto;

import java.math.BigDecimal;

public record ProdutoRapidoResponse(Long id, String nome, String codigoBarras, BigDecimal precoVenda,
        int quantidadeEstoque, boolean disponivel) {

    public static ProdutoRapidoResponse of(Produto p) {
        return new ProdutoRapidoResponse(p.getId(), p.getNome(), p.getCodigoBarras(), p.getPrecoVenda(),
                p.getQuantidadeEstoque(), p.getQuantidadeEstoque() > 0);
    }
}
