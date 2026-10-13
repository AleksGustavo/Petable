package br.com.tecnodreamer.petconnect.produto.web;

import br.com.tecnodreamer.petconnect.pet.web.CatalogoItem;
import br.com.tecnodreamer.petconnect.produto.domain.MovimentacaoEstoque;
import br.com.tecnodreamer.petconnect.produto.domain.TipoMovimentacao;

import java.time.Instant;

public record MovimentacaoResponse(Long id, Long produtoId, TipoMovimentacao tipo, int quantidade, int saldoAnterior,
        int saldoResultante, Long compraId, Long vendaId, CatalogoItem usuario, String motivo, Instant createdAt) {

    public static MovimentacaoResponse of(MovimentacaoEstoque m) {
        return new MovimentacaoResponse(m.getId(), m.getProduto().getId(), m.getTipo(), m.getQuantidade(),
                m.getSaldoAnterior(), m.getSaldoResultante(), m.getCompra() == null ? null : m.getCompra().getId(),
                m.getVendaId(), new CatalogoItem(m.getUsuario().getId(), m.getUsuario().getUsername()), m.getMotivo(),
                m.getCreatedAt());
    }
}
