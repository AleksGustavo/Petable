package br.com.tecnodreamer.petconnect.produto.web;

import br.com.tecnodreamer.petconnect.pet.web.CatalogoItem;
import br.com.tecnodreamer.petconnect.produto.domain.TipoMovimentacao;
import br.com.tecnodreamer.petconnect.venda.domain.StatusVenda;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Rastreabilidade de um lote: de onde entrou, para onde saiu e cada movimento do livro-razão. Sem dado de cliente
 * (privacidade): a venda é identificada pelo número, e o recolhimento parte dele.
 */
public record RastreioLoteResponse(LoteResponse lote, List<Entrada> entradas, List<Saida> saidas, List<Movimento> movimentos,
        long totalMovimentos) {

    public record Entrada(Long compraId, CatalogoItem fornecedor, LocalDate dataCompra, LocalDate dataEntrega, int quantidade,
            BigDecimal custoUnitario) {
    }

    public record Saida(Long vendaId, LocalDateTime dataVenda, StatusVenda statusVenda, int quantidade) {
    }

    public record Movimento(Long id, TipoMovimentacao tipo, int quantidade, int loteSaldoAnterior, int loteSaldoResultante,
            Long compraId, Long vendaId, String motivo, String operacaoId, CatalogoItem usuario, Instant createdAt) {
    }
}
