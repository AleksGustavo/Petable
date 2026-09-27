package br.com.tecnodreamer.petconnect.compra.web;

import br.com.tecnodreamer.petconnect.compra.domain.Compra;
import br.com.tecnodreamer.petconnect.compra.domain.ItemCompra;
import br.com.tecnodreamer.petconnect.compra.domain.StatusCompra;
import br.com.tecnodreamer.petconnect.pet.web.CatalogoItem;

import br.com.tecnodreamer.petconnect.financeiro.web.ContaPagarResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record CompraResponse(Long id, CatalogoItem fornecedor, CatalogoItem funcionario, LocalDate dataCompra,
        LocalDate dataEntrega, StatusCompra status, String formaPagamento, String observacoes, BigDecimal valorTotal,
        int quantidadeItens, List<Item> itens, Instant createdAt, ContaPagarResponse.Resumo contaPagar) {

    public record Item(Long id, CatalogoItem produto, int quantidade, BigDecimal precoCustoUnitario, BigDecimal subtotal) {

        static Item of(ItemCompra i) {
            return new Item(i.getId(), new CatalogoItem(i.getProduto().getId(), i.getProduto().getNome()),
                    i.getQuantidade(), i.getPrecoCustoUnitario(), i.subtotal());
        }
    }

    /** Mesma resposta com o resumo da conta a pagar (compra recebida, etapa 06). */
    public CompraResponse comConta(ContaPagarResponse.Resumo conta) {
        return new CompraResponse(id, fornecedor, funcionario, dataCompra, dataEntrega, status, formaPagamento, observacoes,
                valorTotal, quantidadeItens, itens, createdAt, conta);
    }

    /** Detalhe, com itens. */
    public static CompraResponse of(Compra c) {
        return build(c, c.getItens().stream().map(Item::of).toList(), c.getItens().size());
    }

    /** Lista: sem itens (não carrega a coleção). */
    public static CompraResponse resumo(Compra c) {
        return build(c, null, -1);
    }

    private static CompraResponse build(Compra c, List<Item> itens, int quantidade) {
        var f = c.getFuncionario();
        return new CompraResponse(c.getId(), new CatalogoItem(c.getFornecedor().getId(), c.getFornecedor().getNomeFantasia()),
                f == null ? null : new CatalogoItem(f.getId(), f.getNome()), c.getDataCompra(), c.getDataEntrega(),
                c.getStatus(), c.getFormaPagamento(), c.getObservacoes(), c.getValorTotal(), quantidade, itens,
                c.getCreatedAt(), null);
    }
}
