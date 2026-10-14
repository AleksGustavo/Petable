package br.com.tecnodreamer.petconnect.produto.domain;

import br.com.tecnodreamer.petconnect.produto.EstoqueFixtures;
import br.com.tecnodreamer.petconnect.produto.application.LotesProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoteEstoqueTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 5, 10);

    private static LoteEstoque lote(LocalDate validade, int saldo) {
        LoteEstoque l = new LoteEstoque(EstoqueFixtures.produto(1, 0, "5.00", 1), "L1", validade, null, HOJE, null, null, null);
        if (saldo > 0) {
            l.movimentar(saldo);
        }
        return l;
    }

    @Test
    void nasceDisponivelComSaldoZero() {
        LoteEstoque l = lote(HOJE.plusDays(10), 0);
        assertThat(l.getStatus()).isEqualTo(StatusLote.DISPONIVEL);
        assertThat(l.getSaldo()).isZero();
    }

    @Test
    void codigoEhAparadoEValidado() {
        assertThat(LoteEstoque.normalizarCodigo("  AB-1 ")).isEqualTo("AB-1");
        assertThatThrownBy(() -> LoteEstoque.normalizarCodigo(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LoteEstoque.normalizarCodigo("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LoteEstoque.normalizarCodigo("x".repeat(51))).isInstanceOf(IllegalArgumentException.class);
        assertThat(LoteEstoque.normalizarCodigo("x".repeat(50))).hasSize(50);
    }

    @Test
    void movimentarDevolveSaldoAnteriorENuncaFicaNegativo() {
        LoteEstoque l = lote(HOJE.plusDays(10), 0);
        assertThat(l.movimentar(5)).isZero();
        assertThat(l.movimentar(-5)).isEqualTo(5);
        assertThat(l.getSaldo()).isZero();
        assertThatThrownBy(() -> l.movimentar(-1)).isInstanceOf(IllegalStateException.class);
        assertThat(l.getSaldo()).isZero();
    }

    @Test
    void vencimentoComecaNoDiaSeguinteAValidade() {
        LoteEstoque l = lote(HOJE, 1);
        assertThat(l.isVencidoEm(HOJE)).isFalse();
        assertThat(l.isVencidoEm(HOJE.plusDays(1))).isTrue();
        assertThat(lote(null, 1).isVencidoEm(HOJE.plusYears(50))).isFalse();
    }

    @Test
    void vendavelAteADataInclusiveComMargemZero() {
        LoteEstoque l = lote(HOJE, 1);
        assertThat(l.isVendavelEm(HOJE, 0)).isTrue();
        assertThat(l.isVendavelEm(HOJE.plusDays(1), 0)).isFalse();
    }

    @Test
    void margemBloqueiaAntesDoVencimento() {
        LoteEstoque l = lote(HOJE.plusDays(30), 1);
        assertThat(l.isVendavelEm(HOJE, 29)).isTrue();
        assertThat(l.isVendavelEm(HOJE, 30)).isTrue();
        assertThat(l.isVendavelEm(HOJE, 31)).isFalse();
    }

    @Test
    void bloqueadoOuSemSaldoNaoEhVendavelELoteSemValidadeNuncaVence() {
        LoteEstoque l = lote(HOJE.plusDays(30), 3);
        l.bloquear();
        assertThat(l.isVendavelEm(HOJE, 0)).isFalse();
        l.desbloquear();
        assertThat(l.isVendavelEm(HOJE, 0)).isTrue();
        assertThat(lote(HOJE.plusDays(30), 0).isVendavelEm(HOJE, 0)).isFalse();
        assertThat(lote(null, 1).isVendavelEm(HOJE.plusYears(50), 999)).isTrue();
    }

    @Test
    void margemDoProdutoSobrescreveAGlobal() {
        LotesProperties p = new LotesProperties(false, 7);
        assertThat(p.margemDoProduto(null)).isEqualTo(7);
        assertThat(p.margemDoProduto(0)).isZero();
        assertThat(p.margemDoProduto(30)).isEqualTo(30);
    }

    @Test
    void politicaDeLoteDoProdutoRecusaMargemNegativaEControleNasceDesligado() {
        Produto p = EstoqueFixtures.produto(1, 0, "5.00", 1);
        assertThat(p.isControlaLote()).isFalse();
        assertThat(p.isPermiteSemValidade()).isFalse();
        assertThat(p.getDiasBloqueioValidade()).isNull();
        assertThatThrownBy(() -> p.definirPoliticaDeLote(false, -1)).isInstanceOf(IllegalArgumentException.class);
        p.definirPoliticaDeLote(true, 15);
        p.ativarControleLote();
        assertThat(p.isControlaLote()).isTrue();
        assertThat(p.isPermiteSemValidade()).isTrue();
        assertThat(p.getDiasBloqueioValidade()).isEqualTo(15);
    }
}
