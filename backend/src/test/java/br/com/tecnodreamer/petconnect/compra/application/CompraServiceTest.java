package br.com.tecnodreamer.petconnect.compra.application;

import br.com.tecnodreamer.petconnect.financeiro.application.FinanceiroService;
import br.com.tecnodreamer.petconnect.compra.domain.Compra;
import br.com.tecnodreamer.petconnect.compra.domain.StatusCompra;
import br.com.tecnodreamer.petconnect.compra.infrastructure.CompraRepository;
import br.com.tecnodreamer.petconnect.compra.web.CompraRequest;
import br.com.tecnodreamer.petconnect.compra.web.CompraResponse;
import br.com.tecnodreamer.petconnect.fornecedor.domain.Fornecedor;
import br.com.tecnodreamer.petconnect.fornecedor.infrastructure.FornecedorRepository;
import br.com.tecnodreamer.petconnect.funcionario.infrastructure.FuncionarioRepository;
import br.com.tecnodreamer.petconnect.produto.application.CustoMedioService;
import br.com.tecnodreamer.petconnect.produto.application.EstoqueService;
import br.com.tecnodreamer.petconnect.produto.domain.MovimentacaoEstoque;
import br.com.tecnodreamer.petconnect.produto.domain.Produto;
import br.com.tecnodreamer.petconnect.produto.domain.TipoMovimentacao;
import br.com.tecnodreamer.petconnect.produto.infrastructure.MovimentacaoEstoqueRepository;
import br.com.tecnodreamer.petconnect.produto.infrastructure.ProdutoRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static br.com.tecnodreamer.petconnect.pet.PetFixtures.CLOCK;
import static br.com.tecnodreamer.petconnect.pet.PetFixtures.HOJE;
import static br.com.tecnodreamer.petconnect.produto.EstoqueFixtures.compra;
import static br.com.tecnodreamer.petconnect.produto.EstoqueFixtures.fornecedor;
import static br.com.tecnodreamer.petconnect.produto.EstoqueFixtures.produto;
import static br.com.tecnodreamer.petconnect.usuario.UsuarioFixtures.ADMIN;
import static br.com.tecnodreamer.petconnect.usuario.UsuarioFixtures.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompraServiceTest {

    @Mock CompraRepository compras;
    @Mock FornecedorRepository fornecedores;
    @Mock FuncionarioRepository funcionarios;
    @Mock ProdutoRepository produtos;
    @Mock MovimentacaoEstoqueRepository movimentacoes;
    @Mock UsuarioRepository usuarios;
    @Mock AuditoriaService auditoria;
    @Mock FinanceiroService financeiro;

    CompraService service;
    final Fornecedor fornecedor = fornecedor(4, true);
    final Produto racao = produto(10, 10, "20.00", 5);
    final Produto brinquedo = produto(12, 0, null, 2);

    @BeforeEach
    void setUp() {
        EstoqueService estoque = new EstoqueService(produtos, movimentacoes, usuarios, new CustoMedioService(), auditoria);
        service = new CompraService(compras, fornecedores, funcionarios, produtos, estoque, auditoria, financeiro, CLOCK);
        when(fornecedores.findById(4L)).thenReturn(Optional.of(fornecedor));
        when(produtos.findAllById(anyCollection())).thenReturn(List.of(racao, brinquedo));
        when(produtos.travarEmOrdem(anyCollection())).thenReturn(List.of(racao, brinquedo));
        when(compras.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(movimentacoes.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(usuarios.findById(1L)).thenReturn(Optional.of(usuario(1, "admin", ADMIN, "h")));
    }

    private static CompraRequest.Item item(long produtoId, int qtd, String custo) {
        return new CompraRequest.Item(produtoId, qtd, new BigDecimal(custo));
    }

    private static CompraRequest req(List<CompraRequest.Item> itens) {
        return new CompraRequest(4L, null, HOJE, " BOLETO ", null, itens);
    }

    /** Compra PEDIDO já "persistida": racao 5 × 26,00 e brinquedo 10 × 30,50. */
    private Compra pedido() {
        Compra c = compra(50, fornecedor, HOJE.minusDays(2));
        c.adicionarItem(brinquedo, 10, new BigDecimal("30.50"));
        c.adicionarItem(racao, 5, new BigDecimal("26.00"));
        when(compras.travar(50L)).thenReturn(Optional.of(c));
        return c;
    }

    @Test
    void deveCriarPedidoComItens() {
        CompraResponse r = service.criar(req(List.of(item(10, 10, "120.50"), item(12, 10, "30.50"))));

        assertThat(r.status()).isEqualTo(StatusCompra.PEDIDO);
        assertThat(r.dataEntrega()).isNull();
        assertThat(r.formaPagamento()).isEqualTo("BOLETO");
        assertThat(r.itens()).hasSize(2);
        assertThat(racao.getQuantidadeEstoque()).isEqualTo(10);
        verify(auditoria).registrar(eq("COMPRA_CRIADA"), eq("compra"), any(), anyMap());
    }

    @Test
    void deveCalcularValorTotalDosItens() {
        CompraResponse r = service.criar(req(List.of(item(10, 10, "120.50"), item(12, 3, "30.33"))));

        // 10 × 120,50 + 3 × 30,33 = 1205,00 + 90,99
        assertThat(r.valorTotal()).isEqualByComparingTo("1295.99");
        assertThat(r.itens().get(1).subtotal()).isEqualByComparingTo("90.99");
    }

    @Test
    void deveRejeitarCompraSemItens() {
        assertThatThrownBy(() -> service.criar(req(List.of())))
                .isInstanceOf(CampoInvalidoException.class).extracting("campo").isEqualTo("itens");
    }

    @Test
    void deveRejeitarProdutoDuplicado() {
        assertThatThrownBy(() -> service.criar(req(List.of(item(10, 1, "1.00"), item(10, 2, "1.00")))))
                .isInstanceOf(CampoInvalidoException.class).hasMessageContaining("mais de uma vez");
    }

    @Test
    void deveRejeitarFornecedorInativo() {
        fornecedor.desativar();

        assertThatThrownBy(() -> service.criar(req(List.of(item(10, 1, "1.00")))))
                .isInstanceOf(CampoInvalidoException.class).extracting("campo").isEqualTo("fornecedorId");
    }

    @Test
    void deveRejeitarProdutoInativo() {
        brinquedo.desativar();

        assertThatThrownBy(() -> service.criar(req(List.of(item(10, 1, "1.00"), item(12, 1, "1.00")))))
                .isInstanceOf(CampoInvalidoException.class).hasMessageContaining("12");
        verify(compras, never()).saveAndFlush(any());
    }

    @Test
    void deveReceberCompraPedido() {
        Compra c = pedido();

        CompraResponse r = service.receber(50L, HOJE, HOJE.plusDays(30), 1L);

        assertThat(r.status()).isEqualTo(StatusCompra.RECEBIDO);
        assertThat(c.getDataEntrega()).isEqualTo(HOJE);
        verify(auditoria).registrar(eq("COMPRA_RECEBIDA"), eq("compra"), eq(50L), anyMap());
    }

    @Test
    void deveAtualizarSaldoAoReceber() {
        pedido();

        service.receber(50L, HOJE, HOJE.plusDays(30), 1L);

        assertThat(racao.getQuantidadeEstoque()).isEqualTo(15);
        assertThat(brinquedo.getQuantidadeEstoque()).isEqualTo(10);
    }

    @Test
    void deveAtualizarCustoMedioAoReceber() {
        pedido();

        service.receber(50L, HOJE, HOJE.plusDays(30), 1L);

        // (10 × 20,00 + 5 × 26,00) / 15 = 22,00; brinquedo sem saldo → custo da compra.
        assertThat(racao.getPrecoCusto()).isEqualByComparingTo("22.00");
        assertThat(brinquedo.getPrecoCusto()).isEqualByComparingTo("30.50");
    }

    @Test
    void deveCriarMovimentacaoParaCadaProduto() {
        pedido();

        service.receber(50L, HOJE, HOJE.plusDays(30), 1L);

        ArgumentCaptor<MovimentacaoEstoque> captor = ArgumentCaptor.forClass(MovimentacaoEstoque.class);
        verify(movimentacoes, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).allMatch(m -> m.getTipo() == TipoMovimentacao.ENTRADA_COMPRA && m.getCompra().getId() == 50L);
        // Entradas na ordem crescente de id do produto (mesma ordem dos locks).
        assertThat(captor.getAllValues()).extracting(m -> m.getProduto().getId()).containsExactly(10L, 12L);
        assertThat(captor.getAllValues().getFirst().getSaldoAnterior()).isEqualTo(10);
        assertThat(captor.getAllValues().getFirst().getSaldoResultante()).isEqualTo(15);
    }

    @Test
    void deveCancelarCompraPedido() {
        Compra c = pedido();

        service.cancelar(50L, " Fornecedor sem estoque ");

        assertThat(c.getStatus()).isEqualTo(StatusCompra.CANCELADO);
        assertThat(c.getObservacoes()).isEqualTo("Obs original\nCancelamento: Fornecedor sem estoque");
        assertThat(racao.getQuantidadeEstoque()).isEqualTo(10);
        verify(auditoria).registrar(eq("COMPRA_CANCELADA"), eq("compra"), eq(50L), anyMap());
    }

    @Test
    void deveRejeitarRecebimentoDeCompraRecebida() {
        pedido();
        service.receber(50L, HOJE, HOJE.plusDays(30), 1L);

        assertThatThrownBy(() -> service.receber(50L, HOJE, HOJE.plusDays(30), 1L)).isInstanceOf(ConflictException.class);
        assertThat(racao.getQuantidadeEstoque()).isEqualTo(15);
    }

    @Test
    void deveRejeitarRecebimentoDeCompraCancelada() {
        pedido();
        service.cancelar(50L, "Motivo válido");

        assertThatThrownBy(() -> service.receber(50L, HOJE, HOJE.plusDays(30), 1L)).isInstanceOf(ConflictException.class);
        assertThat(racao.getQuantidadeEstoque()).isEqualTo(10);
    }

    @Test
    void deveRejeitarCancelamentoDeCompraRecebida() {
        pedido();
        service.receber(50L, HOJE, HOJE.plusDays(30), 1L);

        assertThatThrownBy(() -> service.cancelar(50L, "Motivo válido")).isInstanceOf(ConflictException.class);
    }

    @Test
    void deveRejeitarCancelamentoDeCompraCancelada() {
        pedido();
        service.cancelar(50L, "Motivo válido");

        assertThatThrownBy(() -> service.cancelar(50L, "Outro motivo")).isInstanceOf(ConflictException.class);
    }

    @Test
    void deveFazerRollbackIntegralSeRecebimentoFalhar() {
        EstoqueService estoqueQueFalha = mock(EstoqueService.class);
        doThrow(new IllegalStateException("falha no 2º item")).when(estoqueQueFalha)
                .registrarEntradaCompra(eq(brinquedo), anyInt(), any(), any(), anyLong());
        CompraService comFalha = new CompraService(compras, fornecedores, funcionarios, produtos, estoqueQueFalha, auditoria, financeiro, CLOCK);
        Compra c = pedido();

        assertThatThrownBy(() -> comFalha.receber(50L, HOJE, HOJE.plusDays(30), 1L)).isInstanceOf(IllegalStateException.class);

        // A exceção sai do método @Transactional: o banco desfaz tudo. Aqui garantimos que
        // a compra não foi marcada como recebida nem salva, e nada foi auditado.
        assertThat(c.getStatus()).isEqualTo(StatusCompra.PEDIDO);
        assertThat(c.getDataEntrega()).isNull();
        verify(compras, never()).saveAndFlush(any());
        verify(auditoria, never()).registrar(eq("COMPRA_RECEBIDA"), any(), any(), anyMap());
    }

    @Test
    void deveRejeitarDatasInvalidasNoRecebimento() {
        pedido();

        assertThatThrownBy(() -> service.receber(50L, HOJE.plusDays(1), HOJE.plusDays(30), 1L))
                .isInstanceOf(CampoInvalidoException.class).extracting("campo").isEqualTo("dataEntrega");
        assertThatThrownBy(() -> service.receber(50L, HOJE.minusDays(3), HOJE.plusDays(30), 1L))
                .isInstanceOf(CampoInvalidoException.class).extracting("campo").isEqualTo("dataEntrega");
        assertThat(LocalDate.now(CLOCK)).isEqualTo(HOJE);
    }

    // ------------------------------------------------------------ etapa 06: conta a pagar no recebimento

    @Test
    void recebimentoDeveCriarContaAPagarSemSaida() {
        pedido();

        service.receber(50L, HOJE, HOJE.plusDays(30), 1L);

        verify(financeiro).criarContaPagar(any(), eq(HOJE.plusDays(30)));
        verify(financeiro, never()).pagar(any(), any(), any());
    }

    @Test
    void deveRejeitarVencimentoForaDaJanela() {
        pedido();

        assertThatThrownBy(() -> service.receber(50L, HOJE, HOJE.minusDays(1), 1L))
                .isInstanceOf(CampoInvalidoException.class).extracting("campo").isEqualTo("dataVencimento");
        assertThatThrownBy(() -> service.receber(50L, HOJE, HOJE.plusDays(366), 1L))
                .isInstanceOf(CampoInvalidoException.class).extracting("campo").isEqualTo("dataVencimento");
        verify(financeiro, never()).criarContaPagar(any(), any());
    }
}
