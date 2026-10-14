package br.com.tecnodreamer.petconnect.produto.application;

import br.com.tecnodreamer.petconnect.produto.EstoqueFixtures;
import br.com.tecnodreamer.petconnect.produto.application.AlocadorFefo.Alocacao;
import br.com.tecnodreamer.petconnect.produto.domain.LoteEstoque;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlocadorFefoTest {

    private static LoteEstoque lote(String codigo, int saldo) {
        LoteEstoque l = new LoteEstoque(EstoqueFixtures.produto(1, 0, "5.00", 1), codigo, LocalDate.of(2030, 1, 1), null,
                LocalDate.of(2026, 1, 1), null, null, null);
        if (saldo > 0) {
            l.movimentar(saldo);
        }
        return l;
    }

    private static List<String> resumo(List<Alocacao> a) {
        return a.stream().map(x -> x.lote().getCodigoLote() + "=" + x.quantidade()).toList();
    }

    @Test
    void tresDoLoteQueVenceAntesEDoisDoOutro() {
        var r = AlocadorFefo.alocar(List.of(lote("A", 3), lote("B", 7)), 5);
        assertThat(r).hasValueSatisfying(a -> assertThat(resumo(a)).containsExactly("A=3", "B=2"));
    }

    @Test
    void quantidadeQueCabeNoPrimeiroLoteNaoToca_oSegundo() {
        var r = AlocadorFefo.alocar(List.of(lote("A", 3), lote("B", 7)), 2);
        assertThat(r).hasValueSatisfying(a -> assertThat(resumo(a)).containsExactly("A=2"));
    }

    @Test
    void quantidadeExataEsgotaTodosOsLotes() {
        var r = AlocadorFefo.alocar(List.of(lote("A", 3), lote("B", 7)), 10);
        assertThat(r).hasValueSatisfying(a -> assertThat(resumo(a)).containsExactly("A=3", "B=7"));
    }

    @Test
    void faltaDeSaldoDevolveVazioSemAlterarOsLotes() {
        LoteEstoque a = lote("A", 3);
        assertThat(AlocadorFefo.alocar(List.of(a), 4)).isEmpty();
        assertThat(a.getSaldo()).isEqualTo(3);
        assertThat(AlocadorFefo.alocar(List.of(), 1)).isEmpty();
    }

    @Test
    void ignoraLoteSemSaldo() {
        var r = AlocadorFefo.alocar(List.of(lote("VAZIO", 0), lote("B", 7)), 2);
        assertThat(r).hasValueSatisfying(a -> assertThat(resumo(a)).containsExactly("B=2"));
    }

    @Test
    void quantidadeNaoPositivaEErroDoChamador() {
        assertThatThrownBy(() -> AlocadorFefo.alocar(List.of(lote("A", 3)), 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AlocadorFefo.alocar(List.of(lote("A", 3)), -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
