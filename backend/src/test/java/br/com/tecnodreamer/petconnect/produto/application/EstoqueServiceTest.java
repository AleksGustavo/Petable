package br.com.tecnodreamer.petconnect.produto.application;

import br.com.tecnodreamer.petconnect.produto.domain.MovimentacaoEstoque;
import br.com.tecnodreamer.petconnect.produto.domain.Produto;
import br.com.tecnodreamer.petconnect.produto.domain.TipoMovimentacao;
import br.com.tecnodreamer.petconnect.produto.infrastructure.MovimentacaoEstoqueRepository;
import br.com.tecnodreamer.petconnect.produto.infrastructure.ProdutoRepository;
import br.com.tecnodreamer.petconnect.produto.web.MovimentacaoResponse;
import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;
import br.com.tecnodreamer.petconnect.shared.error.ConflictException;
import br.com.tecnodreamer.petconnect.usuario.infrastructure.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static br.com.tecnodreamer.petconnect.produto.EstoqueFixtures.produto;
import static br.com.tecnodreamer.petconnect.usuario.UsuarioFixtures.ADMIN;
import static br.com.tecnodreamer.petconnect.usuario.UsuarioFixtures.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EstoqueServiceTest {

    @Mock ProdutoRepository produtos;
    @Mock MovimentacaoEstoqueRepository movimentacoes;
    @Mock UsuarioRepository usuarios;
    @Mock AuditoriaService auditoria;

    EstoqueService service;
    Produto racao;

    @BeforeEach
    void setUp() {
        service = new EstoqueService(produtos, movimentacoes, usuarios, new CustoMedioService(), auditoria);
        racao = produto(10, 10, "20.00", 5);
        when(produtos.travar(10L)).thenReturn(Optional.of(racao));
        when(usuarios.findById(1L)).thenReturn(Optional.of(usuario(1, "admin", ADMIN, "h")));
        when(movimentacoes.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void deveRealizarAjustePositivo() {
        MovimentacaoResponse m = service.ajustar(10L, 5, "Contagem física", 1L);

        assertThat(racao.getQuantidadeEstoque()).isEqualTo(15);
        assertThat(m.tipo()).isEqualTo(TipoMovimentacao.AJUSTE);
        assertThat(m.quantidade()).isEqualTo(5);
        verify(produtos).travar(10L);
    }

    @Test
    void deveRealizarAjusteNegativo() {
        service.ajustar(10L, -3, "Três unidades avariadas", 1L);

        assertThat(racao.getQuantidadeEstoque()).isEqualTo(7);
    }

    @Test
    void deveRejeitarAjusteZero() {
        assertThatThrownBy(() -> service.ajustar(10L, 0, "Motivo qualquer", 1L))
                .isInstanceOf(CampoInvalidoException.class).extracting("campo").isEqualTo("quantidade");
        verify(produtos, never()).travar(any());
    }

    @Test
    void deveRejeitarAjusteSemMotivo() {
        assertThatThrownBy(() -> service.ajustar(10L, 1, "  ab ", 1L))
                .isInstanceOf(CampoInvalidoException.class).extracting("campo").isEqualTo("motivo");
    }

    @Test
    void deveRejeitarAjusteNegativoMaiorQueSaldo() {
        assertThatThrownBy(() -> service.ajustar(10L, -11, "Baixa indevida", 1L))
                .isInstanceOf(ConflictException.class).hasMessage("O ajuste resultaria em estoque negativo.");
        assertThat(racao.getQuantidadeEstoque()).isEqualTo(10);
        verify(movimentacoes, never()).save(any());
    }

    @Test
    void devePreservarPrecoCustoNoAjuste() {
        service.ajustar(10L, 50, "Entrada por doação", 1L);

        assertThat(racao.getPrecoCusto()).isEqualByComparingTo("20.00");
    }

    @Test
    void deveRegistrarSaldoAnteriorEResultante() {
        service.ajustar(10L, -4, "Correção de contagem", 1L);

        ArgumentCaptor<MovimentacaoEstoque> captor = ArgumentCaptor.forClass(MovimentacaoEstoque.class);
        verify(movimentacoes).save(captor.capture());
        assertThat(captor.getValue().getSaldoAnterior()).isEqualTo(10);
        assertThat(captor.getValue().getSaldoResultante()).isEqualTo(6);
        assertThat(captor.getValue().getMotivo()).isEqualTo("Correção de contagem");
        verify(auditoria).registrar(eq("ESTOQUE_AJUSTADO"), eq("produto"), eq(10L),
                argThat(d -> Integer.valueOf(10).equals(d.get("saldoAnterior")) && Integer.valueOf(6).equals(d.get("saldoResultante"))));
    }
    private br.com.tecnodreamer.petconnect.venda.domain.Venda venda(long id) {
        var v = new br.com.tecnodreamer.petconnect.venda.domain.Venda(null, null, null, java.time.LocalDateTime.of(2026, 9, 25, 12, 0),
                br.com.tecnodreamer.petconnect.venda.domain.FormaPagamento.PIX, null, null);
        org.springframework.test.util.ReflectionTestUtils.setField(v, "id", id);
        return v;
    }

    @Test
    void deveRegistrarSaidaVendaComQuantidadeNegativa() {
        MovimentacaoEstoque m = service.registrarSaidaVenda(racao, 4, venda(91), 1L);

        assertThat(m.getTipo()).isEqualTo(TipoMovimentacao.SAIDA_VENDA);
        assertThat(m.getQuantidade()).isEqualTo(-4);
        assertThat(m.getSaldoAnterior()).isEqualTo(10);
        assertThat(m.getSaldoResultante()).isEqualTo(6);
        assertThat(m.getVendaId()).isEqualTo(91L);
        assertThat(racao.getQuantidadeEstoque()).isEqualTo(6);
    }

    @Test
    void deveRejeitarSaidaMaiorQueSaldo() {
        assertThatThrownBy(() -> service.registrarSaidaVenda(racao, 11, venda(91), 1L))
                .isInstanceOf(br.com.tecnodreamer.petconnect.produto.domain.EstoqueInsuficienteException.class)
                .hasMessageContaining("disponível 10, solicitado 11");
        assertThat(racao.getQuantidadeEstoque()).isEqualTo(10);
        verify(movimentacoes, never()).save(any());
    }

    @Test
    void deveRegistrarEstornoVenda() {
        racao.desativar();

        MovimentacaoEstoque m = service.registrarEstornoVenda(racao, 3, venda(91), 1L);

        assertThat(m.getTipo()).isEqualTo(TipoMovimentacao.ESTORNO_VENDA);
        assertThat(m.getQuantidade()).isEqualTo(3);
        assertThat(m.getVendaId()).isEqualTo(91L);
        assertThat(racao.getQuantidadeEstoque()).isEqualTo(13);
    }

    @Test
    void saidaEEstornoNaoAlteramCusto() {
        service.registrarSaidaVenda(racao, 5, venda(91), 1L);
        service.registrarEstornoVenda(racao, 5, venda(91), 1L);

        assertThat(racao.getPrecoCusto()).isEqualByComparingTo("20.00");
        assertThat(racao.getQuantidadeEstoque()).isEqualTo(10);
    }
}
