package br.com.tecnodreamer.petconnect.venda.web;

import br.com.tecnodreamer.petconnect.pet.web.CatalogoItem;
import br.com.tecnodreamer.petconnect.usuario.domain.Usuario;
import br.com.tecnodreamer.petconnect.venda.domain.FormaPagamento;
import br.com.tecnodreamer.petconnect.venda.domain.ItemVenda;
import br.com.tecnodreamer.petconnect.venda.domain.StatusVenda;
import br.com.tecnodreamer.petconnect.venda.domain.Venda;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record VendaResponse(Long id, LocalDateTime dataVenda, StatusVenda status, CatalogoItem cliente,
        CatalogoItem funcionario, CatalogoItem operador, List<Item> itens, int quantidadeItens, BigDecimal subtotal,
        BigDecimal desconto, BigDecimal valorTotal, FormaPagamento formaPagamento, BigDecimal valorRecebido,
        BigDecimal troco, String observacoes, LocalDateTime canceladaEm, CatalogoItem canceladaPor,
        String motivoCancelamento, Long caixaId, Long lancamentoFinanceiroId) {

    /** {@code lotes}: de quais lotes saiu o item (vazio em produto sem controle de lote). */
    public record Item(Long id, CatalogoItem produto, int quantidade, BigDecimal precoUnitario, BigDecimal subtotal,
            List<LoteAlocado> lotes) {
        static Item of(ItemVenda i) {
            return new Item(i.getId(), new CatalogoItem(i.getProduto().getId(), i.getProduto().getNome()),
                    i.getQuantidade(), i.getPrecoUnitario(), i.subtotal(), List.of());
        }
    }

    public record LoteAlocado(Long loteId, String codigoLote, java.time.LocalDate validade, int quantidade) {
    }

    /** Mesma resposta com as alocações de lote de cada item (detalhe da venda). */
    public VendaResponse comLotes(java.util.Map<Long, List<LoteAlocado>> porItem) {
        if (itens == null || porItem.isEmpty()) {
            return this;
        }
        List<Item> novos = itens.stream().map(i -> new Item(i.id(), i.produto(), i.quantidade(), i.precoUnitario(), i.subtotal(),
                porItem.getOrDefault(i.id(), List.of()))).toList();
        return new VendaResponse(id, dataVenda, status, cliente, funcionario, operador, novos, quantidadeItens, subtotal,
                desconto, valorTotal, formaPagamento, valorRecebido, troco, observacoes, canceladaEm, canceladaPor,
                motivoCancelamento, caixaId, lancamentoFinanceiroId);
    }

    /** Detalhe, com itens. */
    public static VendaResponse of(Venda v) {
        return build(v, v.getItens().stream().map(Item::of).toList(), v.getItens().size());
    }

    /** Lista: sem itens (não carrega a coleção). */
    public static VendaResponse resumo(Venda v) {
        return build(v, null, -1);
    }

    private static VendaResponse build(Venda v, List<Item> itens, int quantidade) {
        var c = v.getCliente();
        var f = v.getFuncionario();
        // subtotal derivado: total + desconto (não depende de carregar os itens)
        return new VendaResponse(v.getId(), v.getDataVenda(), v.getStatus(),
                c == null ? null : new CatalogoItem(c.getId(), c.getNome()),
                f == null ? null : new CatalogoItem(f.getId(), f.getNome()), usuario(v.getUsuario()), itens, quantidade,
                v.getValorTotal().add(v.getDesconto()), v.getDesconto(), v.getValorTotal(), v.getFormaPagamento(),
                v.getValorRecebido(), v.troco(), v.getObservacoes(), v.getCanceladaEm(), usuario(v.getCanceladaPor()),
                v.getMotivoCancelamento(), v.getCaixa() == null ? null : v.getCaixa().getId(), null);
    }

    /** Mesma resposta com o id do lançamento financeiro da venda (etapa 06). */
    public VendaResponse comLancamento(Long lancamentoId) {
        return new VendaResponse(id, dataVenda, status, cliente, funcionario, operador, itens, quantidadeItens, subtotal,
                desconto, valorTotal, formaPagamento, valorRecebido, troco, observacoes, canceladaEm, canceladaPor,
                motivoCancelamento, caixaId, lancamentoId);
    }

    private static CatalogoItem usuario(Usuario u) {
        return u == null ? null : new CatalogoItem(u.getId(), u.getUsername());
    }
}
