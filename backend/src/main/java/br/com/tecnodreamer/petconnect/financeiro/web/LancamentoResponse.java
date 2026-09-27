package br.com.tecnodreamer.petconnect.financeiro.web;

import br.com.tecnodreamer.petconnect.financeiro.domain.CategoriaLancamento;
import br.com.tecnodreamer.petconnect.financeiro.domain.Lancamento;
import br.com.tecnodreamer.petconnect.financeiro.domain.TipoLancamento;
import br.com.tecnodreamer.petconnect.pet.web.CatalogoItem;
import br.com.tecnodreamer.petconnect.venda.domain.FormaPagamento;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * {@code estornadoPorId}: id do estorno que corrige este lançamento (só no detalhe);
 * {@code estornavel}: manual, ainda não estornado e não é ele mesmo um estorno.
 */
public record LancamentoResponse(Long id, TipoLancamento tipo, CategoriaLancamento categoria, String descricao,
        BigDecimal valor, LocalDate dataMovimentacao, FormaPagamento formaPagamento, Long usuarioId, CatalogoItem usuario,
        Long caixaId, OrigemResponse origem, Long estornoDeId, Long estornadoPorId, String motivoEstorno,
        String observacoes, boolean estornavel) {

    public static LancamentoResponse of(Lancamento l) {
        return of(l, null);
    }

    public static LancamentoResponse of(Lancamento l, Long estornadoPorId) {
        var u = l.getUsuario();
        boolean estornavel = l.isManual() && l.getEstornoDeId() == null && estornadoPorId == null;
        return new LancamentoResponse(l.getId(), l.getTipo(), l.getCategoria(), l.getDescricao(), l.getValor(),
                l.getDataMovimentacao(), l.getFormaPagamento(), u == null ? null : u.getId(),
                u == null ? null : new CatalogoItem(u.getId(), u.getUsername()),
                l.getCaixa() == null ? null : l.getCaixa().getId(), OrigemResponse.de(l.getChaveOrigem()),
                l.getEstornoDeId(), estornadoPorId, l.getMotivoEstorno(), l.getObservacoes(), estornavel);
    }
}
