package br.com.tecnodreamer.petconnect.produto.web;

import br.com.tecnodreamer.petconnect.pet.web.CatalogoItem;
import br.com.tecnodreamer.petconnect.produto.domain.Produto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProdutoResponse(Long id, String nome, String descricao, CatalogoItem categoria, CatalogoItem fornecedorPadrao,
        String codigoBarras, BigDecimal precoCusto, BigDecimal precoVenda, int quantidadeEstoque, int estoqueMinimo,
        boolean abaixoDoMinimo, boolean ativo, Instant createdAt, Instant updatedAt, boolean controlaLote,
        boolean permiteSemValidade, Integer diasBloqueioValidade) {

    public static ProdutoResponse of(Produto p) {
        var f = p.getFornecedorPadrao();
        return new ProdutoResponse(p.getId(), p.getNome(), p.getDescricao(),
                new CatalogoItem(p.getCategoria().getId(), p.getCategoria().getNome()),
                f == null ? null : new CatalogoItem(f.getId(), f.getNomeFantasia()),
                p.getCodigoBarras(), p.getPrecoCusto(), p.getPrecoVenda(), p.getQuantidadeEstoque(), p.getEstoqueMinimo(),
                p.isAbaixoDoMinimo(), p.isAtivo(), p.getCreatedAt(), p.getUpdatedAt(), p.isControlaLote(), p.isPermiteSemValidade(),
                p.getDiasBloqueioValidade());
    }
}
