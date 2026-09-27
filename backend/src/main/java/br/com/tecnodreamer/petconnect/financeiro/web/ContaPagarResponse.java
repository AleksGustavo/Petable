package br.com.tecnodreamer.petconnect.financeiro.web;

import br.com.tecnodreamer.petconnect.financeiro.domain.ContaPagar;
import br.com.tecnodreamer.petconnect.financeiro.domain.StatusConta;
import br.com.tecnodreamer.petconnect.pet.web.CatalogoItem;
import br.com.tecnodreamer.petconnect.venda.domain.FormaPagamento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** {@code situacao}: PAGA, VENCIDA, VENCE_HOJE ou A_VENCER (sempre com texto na UI). */
public record ContaPagarResponse(Long id, Long compraId, CatalogoItem fornecedor, String descricao, BigDecimal valorOriginal,
        BigDecimal saldo, LocalDate dataVencimento, StatusConta status, boolean vencida, String situacao,
        LocalDateTime pagaEm, FormaPagamento formaPagamento, CatalogoItem usuarioPagamento, Long lancamentoId) {

    public static ContaPagarResponse of(ContaPagar c, LocalDate hoje, Long lancamentoId) {
        var compra = c.getCompra();
        var f = compra == null ? null : compra.getFornecedor();
        var u = c.getUsuarioPagamento();
        String situacao;
        if (c.getStatus() == StatusConta.PAGA) {
            situacao = "PAGA";
        } else if (c.getDataVencimento().isBefore(hoje)) {
            situacao = "VENCIDA";
        } else if (c.getDataVencimento().isEqual(hoje)) {
            situacao = "VENCE_HOJE";
        } else {
            situacao = "A_VENCER";
        }
        return new ContaPagarResponse(c.getId(), compra == null ? null : compra.getId(),
                f == null ? null : new CatalogoItem(f.getId(), f.getNomeFantasia()), c.getDescricao(), c.getValorOriginal(),
                c.getSaldo(), c.getDataVencimento(), c.getStatus(), "VENCIDA".equals(situacao), situacao, c.getPagaEm(),
                c.getFormaPagamento(), u == null ? null : new CatalogoItem(u.getId(), u.getUsername()), lancamentoId);
    }

    /** Resumo embutido na resposta do recebimento de compra. */
    public record Resumo(Long id, StatusConta status, LocalDate dataVencimento, BigDecimal valorOriginal) {
        public static Resumo of(ContaPagar c) {
            return c == null ? null : new Resumo(c.getId(), c.getStatus(), c.getDataVencimento(), c.getValorOriginal());
        }
    }
}
