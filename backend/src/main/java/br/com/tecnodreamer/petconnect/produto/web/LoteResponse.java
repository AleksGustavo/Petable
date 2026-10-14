package br.com.tecnodreamer.petconnect.produto.web;

import br.com.tecnodreamer.petconnect.produto.domain.LoteEstoque;
import br.com.tecnodreamer.petconnect.produto.domain.StatusLote;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record LoteResponse(Long id, Long produtoId, String produtoNome, String codigoLote, LocalDate validade, int saldo,
        StatusLote status, boolean vencido, boolean vendavel, BigDecimal custoUnitario, LocalDate dataRecebimento,
        Long compraId, Long fornecedorId, Instant createdAt) {

    /** {@code margemDias} é a margem de bloqueio efetiva do produto (D4). */
    public static LoteResponse of(LoteEstoque l, LocalDate hoje, int margemDias) {
        return new LoteResponse(l.getId(), l.getProduto().getId(), l.getProduto().getNome(), l.getCodigoLote(), l.getValidade(),
                l.getSaldo(), l.getStatus(), l.isVencidoEm(hoje), l.isVendavelEm(hoje, margemDias), l.getCustoUnitario(),
                l.getDataRecebimento(), l.getCompra() == null ? null : l.getCompra().getId(),
                l.getFornecedor() == null ? null : l.getFornecedor().getId(), l.getCreatedAt());
    }
}
