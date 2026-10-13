package br.com.tecnodreamer.petconnect.financeiro.web;

import br.com.tecnodreamer.petconnect.financeiro.domain.Caixa;
import br.com.tecnodreamer.petconnect.financeiro.domain.CaixaMovimento;
import br.com.tecnodreamer.petconnect.financeiro.domain.StatusCaixa;
import br.com.tecnodreamer.petconnect.financeiro.domain.TipoMovimentoCaixa;
import br.com.tecnodreamer.petconnect.pet.web.CatalogoItem;
import br.com.tecnodreamer.petconnect.venda.domain.FormaPagamento;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Caixa. Aberto: {@code esperado} é a apuração atual (prévia); fechado: os valores
 * gravados no fechamento, com {@code contado} e {@code diferenca}.
 */
public record CaixaResponse(Long id, CatalogoItem usuario, StatusCaixa status, LocalDateTime abertoEm, BigDecimal fundoAbertura,
        LocalDateTime fechadoEm, Map<FormaPagamento, BigDecimal> esperado, Map<FormaPagamento, BigDecimal> contado,
        Map<FormaPagamento, BigDecimal> diferenca, BigDecimal totalEsperado, BigDecimal diferencaTotal, String motivoDiferenca,
        List<Movimento> movimentos) {

    public record Movimento(Long id, TipoMovimentoCaixa tipo, BigDecimal valor, String motivo, CatalogoItem usuario,
            Instant createdAt) {
        public static Movimento of(CaixaMovimento m) {
            return new Movimento(m.getId(), m.getTipo(), m.getValor(), m.getMotivo(),
                    new CatalogoItem(m.getUsuario().getId(), m.getUsuario().getUsername()), m.getCreatedAt());
        }
    }

    public static CaixaResponse of(Caixa c, Map<FormaPagamento, BigDecimal> esperadoAtual, List<Movimento> movimentos) {
        Map<FormaPagamento, BigDecimal> esperado = c.isAberto() ? esperadoAtual : c.esperado();
        BigDecimal total = esperado == null ? null
                : esperado.values().stream().reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        return new CaixaResponse(c.getId(), new CatalogoItem(c.getUsuario().getId(), c.getUsuario().getUsername()),
                c.getStatus(), c.getAbertoEm(), c.getFundoAbertura(), c.getFechadoEm(), esperado, c.contado(), c.diferenca(),
                total, c.getDiferencaTotal(), c.getMotivoDiferenca(), movimentos);
    }
}
