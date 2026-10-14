package br.com.tecnodreamer.petconnect.venda.application;

import br.com.tecnodreamer.petconnect.financeiro.domain.Lancamento;
import br.com.tecnodreamer.petconnect.financeiro.application.FinanceiroService;
import br.com.tecnodreamer.petconnect.financeiro.application.CaixaService;
import br.com.tecnodreamer.petconnect.cliente.domain.Cliente;
import br.com.tecnodreamer.petconnect.cliente.infrastructure.ClienteRepository;
import br.com.tecnodreamer.petconnect.funcionario.domain.Funcionario;
import br.com.tecnodreamer.petconnect.funcionario.infrastructure.FuncionarioRepository;
import br.com.tecnodreamer.petconnect.produto.application.CustoMedioService;
import br.com.tecnodreamer.petconnect.produto.application.EstoqueService;
import br.com.tecnodreamer.petconnect.produto.domain.EstoqueInsuficienteException;
import br.com.tecnodreamer.petconnect.produto.domain.MovimentacaoEstoque;
import br.com.tecnodreamer.petconnect.produto.domain.Produto;
import br.com.tecnodreamer.petconnect.produto.domain.TipoMovimentacao;
import br.com.tecnodreamer.petconnect.produto.infrastructure.MovimentacaoEstoqueRepository;
import br.com.tecnodreamer.petconnect.produto.infrastructure.ProdutoRepository;
import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;
import br.com.tecnodreamer.petconnect.shared.error.ConflictException;
import br.com.tecnodreamer.petconnect.usuario.domain.Usuario;
import br.com.tecnodreamer.petconnect.usuario.infrastructure.UsuarioRepository;
import br.com.tecnodreamer.petconnect.venda.domain.FormaPagamento;
import br.com.tecnodreamer.petconnect.venda.domain.StatusVenda;
import br.com.tecnodreamer.petconnect.venda.domain.Venda;
import br.com.tecnodreamer.petconnect.venda.infrastructure.VendaRepository;
import br.com.tecnodreamer.petconnect.venda.web.PreviaVendaResponse;
import br.com.tecnodreamer.petconnect.venda.web.VendaRequest;
import br.com.tecnodreamer.petconnect.venda.web.VendaResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static br.com.tecnodreamer.petconnect.pet.PetFixtures.CLOCK;
import static br.com.tecnodreamer.petconnect.pet.PetFixtures.cliente;
import static br.com.tecnodreamer.petconnect.produto.EstoqueFixtures.produto;
import static br.com.tecnodreamer.petconnect.usuario.UsuarioFixtures.ADMIN;
import static br.com.tecnodreamer.petconnect.usuario.UsuarioFixtures.VENDAS;
import static br.com.tecnodreamer.petconnect.usuario.UsuarioFixtures.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VendaServiceTest {

    static final String CHAVE = "0b8f9a52-5f0e-4d0e-9d1c-6e7b2a1c3d4e";

    @Mock VendaRepository vendas;
    @Mock ProdutoRepository produtos;
    @Mock ClienteRepository clientes;
    @Mock FuncionarioRepository funcionarios;
    @Mock UsuarioRepository usuarios;
    @Mock MovimentacaoEstoqueRepository movimentacoes;
    @Mock AuditoriaService auditoria;
    @Mock CaixaService caixas;
    @Mock FinanceiroService financeiro;

    VendaService service;
    final Usuario vendedora = usuario(5, "joana", VENDAS, "h");
    final Usuario admin = usuario(1, "admin", ADMIN, "h");
    Produto racao;
    Produto coleira;

    @BeforeEach
    void setUp() {
        EstoqueService estoque = new EstoqueService(produtos, movimentacoes, usuarios, new CustoMedioService(), auditoria);
        service = new VendaService(vendas, produtos, clientes, funcionarios, usuarios, estoque, org.mockito.Mockito.mock(br.com.tecnodreamer.petconnect.produto.application.EstoqueLoteService.class), org.mockito.Mockito.mock(br.com.tecnodreamer.petconnect.venda.infrastructure.ItemVendaLoteRepository.class), auditoria,
                new VendasProperties(BigDecimal.TEN), caixas, financeiro,
                CLOCK);
        Lancamento entrada = org.mockito.Mockito.mock(Lancamento.class);
        when(entrada.getId()).thenReturn(501L);
        when(financeiro.registrarVenda(any(), any(), any())).thenReturn(entrada);
        racao = comPreco(produto(12, 10, "20.00", 5), "169.90");
        coleira = comPreco(produto(10, 3, "10.00", 1), "30.00");
        when(usuarios.findById(5L)).thenReturn(Optional.of(vendedora));
        when(usuarios.findById(1L)).thenReturn(Optional.of(admin));
        when(produtos.travarEmOrdem(anyCollection())).thenReturn(List.of(coleira, racao));
        when(produtos.findAllById(anyCollection())).thenReturn(List.of(coleira, racao));
        when(vendas.findByChaveIdempotencia(anyString())).thenReturn(Optional.empty());
        when(vendas.saveAndFlush(any())).thenAnswer(inv -> {
            Venda v = inv.getArgument(0);
            if (v.getId() == null) {
                ReflectionTestUtils.setField(v, "id", 91L);
            }
            return v;
        });
        when(movimentacoes.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(funcionarios.findByUsuarioId(any())).thenReturn(Optional.empty());
    }

    private static Produto comPreco(Produto p, String preco) {
        ReflectionTestUtils.setField(p, "precoVenda", new BigDecimal(preco));
        return p;
    }

    private static VendaRequest.Item item(long produtoId, int qtd) {
        return new VendaRequest.Item(produtoId, qtd);
    }

    private static VendaRequest req(List<VendaRequest.Item> itens, String desconto, FormaPagamento forma, String recebido) {
        return new VendaRequest(null, itens, desconto == null ? null : new BigDecimal(desconto), forma,
                recebido == null ? null : new BigDecimal(recebido), null);
    }

    /** 2 × 169,90 + 1 × 30,00 = 369,80 (exemplo da spec). */
    private static VendaRequest padrao(String desconto, FormaPagamento forma, String recebido) {
        return req(List.of(item(12, 2), item(10, 1)), desconto, forma, recebido);
    }

    private Venda vendaConcluida(long id, Usuario operador, LocalDateTime quando) {
        Venda v = new Venda(null, null, operador, quando, FormaPagamento.PIX, null, CHAVE);
        v.adicionarItem(racao, 2);
        v.adicionarItem(coleira, 1);
        v.fechar(BigDecimal.ZERO, null);
        ReflectionTestUtils.setField(v, "id", id);
        return v;
    }

    @Test
    void deveRegistrarVendaComPrecoDoProduto() {
        VendaResponse r = service.registrar(padrao(null, FormaPagamento.PIX, null), CHAVE, 5L).venda();

        assertThat(r.status()).isEqualTo(StatusVenda.CONCLUIDA);
        assertThat(r.itens()).extracting(VendaResponse.Item::precoUnitario)
                .containsExactly(new BigDecimal("169.90"), new BigDecimal("30.00"));
        assertThat(r.dataVenda()).isEqualTo(LocalDateTime.now(CLOCK));
        verify(auditoria).registrar(eq("VENDA_REALIZADA"), eq("venda"), eq(91L), anyMap());
    }

    @Test
    void deveIgnorarPrecoEnviadoPeloCliente() {
        // O contrato não tem como receber preço, total, status, data nem operador.
        assertThat(Arrays.stream(VendaRequest.Item.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("produtoId", "quantidade");
        assertThat(Arrays.stream(VendaRequest.class.getRecordComponents()).map(RecordComponent::getName))
                .doesNotContain("valorTotal", "total", "subtotal", "status", "dataVenda", "operador", "usuarioId");
        ReflectionTestUtils.setField(racao, "precoVenda", new BigDecimal("175.00"));

        VendaResponse r = service.registrar(padrao(null, FormaPagamento.PIX, null), CHAVE, 5L).venda();

        assertThat(r.valorTotal()).isEqualByComparingTo("380.00");
    }

    @Test
    void deveCalcularSubtotalDescontoETotal() {
        VendaResponse r = service.registrar(padrao("5.00", FormaPagamento.PIX, null), CHAVE, 5L).venda();

        assertThat(r.subtotal()).isEqualByComparingTo("369.80");
        assertThat(r.desconto()).isEqualByComparingTo("5.00");
        assertThat(r.valorTotal()).isEqualByComparingTo("364.80");
        assertThat(r.troco()).isNull();
    }

    @Test
    void deveCalcularTrocoEmDinheiro() {
        VendaResponse r = service.registrar(padrao("5.00", FormaPagamento.DINHEIRO, "400.00"), CHAVE, 5L).venda();

        assertThat(r.valorRecebido()).isEqualByComparingTo("400.00");
        assertThat(r.troco()).isEqualByComparingTo("35.20");
    }

    @Test
    void deveExigirValorRecebidoEmDinheiro() {
        assertThatThrownBy(() -> service.registrar(padrao(null, FormaPagamento.DINHEIRO, null), CHAVE, 5L))
                .isInstanceOf(CampoInvalidoException.class).extracting("campo").isEqualTo("valorRecebido");
        // Em outras formas, o valor recebido é ignorado.
        assertThat(service.registrar(padrao(null, FormaPagamento.CARTAO_DEBITO, "999.00"), CHAVE, 5L).venda().valorRecebido())
                .isNull();
    }

    @Test
    void deveRejeitarValorRecebidoMenorQueTotal() {
        assertThatThrownBy(() -> service.registrar(padrao(null, FormaPagamento.DINHEIRO, "369.79"), CHAVE, 5L))
                .isInstanceOf(CampoInvalidoException.class).hasMessage("O valor recebido não pode ser menor que o total.");
        verify(vendas, never()).saveAndFlush(any());
    }

    @Test
    void deveRejeitarVendaSemItens() {
        assertThatThrownBy(() -> service.registrar(req(List.of(), null, FormaPagamento.PIX, null), CHAVE, 5L))
                .isInstanceOf(CampoInvalidoException.class).extracting("campo").isEqualTo("itens");
    }

    @Test
    void deveRejeitarProdutoDuplicado() {
        assertThatThrownBy(() -> service.registrar(req(List.of(item(12, 1), item(12, 1)), null, FormaPagamento.PIX, null),
                CHAVE, 5L)).isInstanceOf(CampoInvalidoException.class).hasMessageContaining("mais de uma vez");
        verify(produtos, never()).travarEmOrdem(anyCollection());
    }

    @Test
    void deveRejeitarProdutoInativo() {
        coleira.desativar();

        assertThatThrownBy(() -> service.registrar(padrao(null, FormaPagamento.PIX, null), CHAVE, 5L))
                .isInstanceOf(CampoInvalidoException.class).extracting("campo").isEqualTo("itens[1].produtoId");
        verify(vendas, never()).saveAndFlush(any());
    }

    @Test
    void deveRejeitarDescontoMaiorOuIgualAoSubtotal() {
        assertThatThrownBy(() -> service.registrar(padrao("369.80", FormaPagamento.PIX, null), CHAVE, 1L))
                .isInstanceOf(CampoInvalidoException.class).hasMessage("O desconto deve ser menor que o subtotal.");
    }

    @Test
    void vendasNaoPodeDarDescontoAcimaDoTeto() {
        // 10% de 369,80 = 36,98; 40,00 é 10,8%.
        assertThatThrownBy(() -> service.registrar(padrao("40.00", FormaPagamento.PIX, null), CHAVE, 5L))
                .isInstanceOf(CampoInvalidoException.class)
                .hasMessage("Desconto acima do permitido para o seu perfil (máximo 10%).");
        assertThat(service.registrar(padrao("36.98", FormaPagamento.PIX, null), CHAVE, 5L).venda().valorTotal())
                .isEqualByComparingTo("332.82");
    }

    @Test
    void adminPodeDarDescontoAcimaDoTeto() {
        VendaResponse r = service.registrar(padrao("40.00", FormaPagamento.PIX, null), CHAVE, 1L).venda();

        assertThat(r.valorTotal()).isEqualByComparingTo("329.80");
    }

    @Test
    void deveBaixarEstoqueDeCadaItemEmOrdemDeId() {
        // Pedido na ordem 12, 10; a baixa acontece na ordem 10, 12 (mesma ordem dos locks).
        service.registrar(padrao(null, FormaPagamento.PIX, null), CHAVE, 5L);

        ArgumentCaptor<MovimentacaoEstoque> captor = ArgumentCaptor.forClass(MovimentacaoEstoque.class);
        verify(movimentacoes, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(m -> m.getProduto().getId()).containsExactly(10L, 12L);
        assertThat(captor.getAllValues()).allMatch(m -> m.getTipo() == TipoMovimentacao.SAIDA_VENDA && m.getVendaId() == 91L);
        assertThat(captor.getAllValues()).extracting(MovimentacaoEstoque::getQuantidade).containsExactly(-1, -2);
        assertThat(racao.getQuantidadeEstoque()).isEqualTo(8);
        assertThat(coleira.getQuantidadeEstoque()).isEqualTo(2);
    }

    @Test
    void deveFalharVendaInteiraSeUmItemNaoTiverEstoque() {
        assertThatThrownBy(() -> service.registrar(req(List.of(item(12, 2), item(10, 4)), null, FormaPagamento.PIX, null),
                CHAVE, 5L))
                .isInstanceOf(EstoqueInsuficienteException.class)
                .hasMessage("Estoque insuficiente para Produto 10: disponível 3, solicitado 4.")
                .satisfies(e -> assertThat(((EstoqueInsuficienteException) e).getPropriedades())
                        .containsEntry("produtoId", 10L).containsEntry("disponivel", 3).containsEntry("solicitado", 4));
        verify(vendas, never()).saveAndFlush(any());
        verify(movimentacoes, never()).save(any());
        assertThat(racao.getQuantidadeEstoque()).isEqualTo(10);
    }

    @Test
    void deveDefinirOperadorEFuncionarioDoUsuarioLogado() {
        Funcionario joana = new Funcionario(vendedora, "52998224725");
        ReflectionTestUtils.setField(joana, "id", 2L);
        ReflectionTestUtils.setField(joana, "nome", "Joana Souza");
        when(funcionarios.findByUsuarioId(5L)).thenReturn(Optional.of(joana));
        Cliente maria = cliente(3L);
        when(clientes.findById(3L)).thenReturn(Optional.of(maria));

        VendaResponse r = service.registrar(new VendaRequest(3L, List.of(item(12, 1)), null, FormaPagamento.PIX, null, null),
                CHAVE, 5L).venda();

        assertThat(r.operador().id()).isEqualTo(5L);
        assertThat(r.funcionario().nome()).isEqualTo("Joana Souza");
        assertThat(r.cliente().id()).isEqualTo(3L);
    }

    @Test
    void deveDevolverVendaExistenteParaMesmaChave() {
        when(vendas.findByChaveIdempotencia(CHAVE)).thenReturn(Optional.of(vendaConcluida(77, vendedora, LocalDateTime.now(CLOCK))));

        ResultadoVenda r = service.registrar(padrao(null, FormaPagamento.PIX, null), CHAVE.toUpperCase(), 5L);

        assertThat(r.repetida()).isTrue();
        assertThat(r.venda().id()).isEqualTo(77L);
        verify(produtos, never()).travarEmOrdem(anyCollection());
        verify(movimentacoes, never()).save(any());
        verify(auditoria, never()).registrar(anyString(), anyString(), any(), anyMap());
        assertThatThrownBy(() -> service.registrar(padrao(null, FormaPagamento.PIX, null), "nao-e-uuid", 5L))
                .isInstanceOf(CampoInvalidoException.class).extracting("campo").isEqualTo("idempotencyKey");
    }

    @Test
    void deveRejeitarChaveDeOutroUsuario() {
        when(vendas.findByChaveIdempotencia(CHAVE)).thenReturn(Optional.of(vendaConcluida(77, admin, LocalDateTime.now(CLOCK))));

        assertThatThrownBy(() -> service.registrar(padrao(null, FormaPagamento.PIX, null), CHAVE, 5L))
                .isInstanceOf(ConflictException.class).hasMessage("Chave de idempotência já utilizada.");
    }

    @Test
    void deveCancelarVendaComEstorno() {
        Venda v = vendaConcluida(91, vendedora, LocalDateTime.now(CLOCK).minusDays(1));
        when(vendas.travar(91L)).thenReturn(Optional.of(v));

        VendaResponse r = service.cancelar(91L, " Cliente desistiu na hora ", 1L);

        assertThat(r.status()).isEqualTo(StatusVenda.CANCELADA);
        assertThat(r.motivoCancelamento()).isEqualTo("Cliente desistiu na hora");
        assertThat(r.canceladaPor().nome()).isEqualTo("admin");
        assertThat(r.canceladaEm()).isEqualTo(LocalDateTime.now(CLOCK));
        assertThat(racao.getQuantidadeEstoque()).isEqualTo(12);
        assertThat(coleira.getQuantidadeEstoque()).isEqualTo(4);
        ArgumentCaptor<MovimentacaoEstoque> captor = ArgumentCaptor.forClass(MovimentacaoEstoque.class);
        verify(movimentacoes, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).allMatch(m -> m.getTipo() == TipoMovimentacao.ESTORNO_VENDA && m.getQuantidade() > 0);
        verify(auditoria).registrar(eq("VENDA_CANCELADA"), eq("venda"), eq(91L), anyMap());
    }

    @Test
    void deveRejeitarCancelamentoDeVendaCancelada() {
        Venda v = vendaConcluida(91, vendedora, LocalDateTime.now(CLOCK));
        v.cancelar(admin, "Motivo anterior", LocalDateTime.now(CLOCK));
        when(vendas.travar(91L)).thenReturn(Optional.of(v));

        assertThatThrownBy(() -> service.cancelar(91L, "Outro motivo", 1L))
                .isInstanceOf(ConflictException.class).hasMessage("A venda já está cancelada.");
        verify(movimentacoes, never()).save(any());
    }

    @Test
    void deveRejeitarCancelamentoForaDoPrazo() {
        when(vendas.travar(91L)).thenReturn(Optional.of(vendaConcluida(91, vendedora, LocalDateTime.now(CLOCK).minusDays(8))));
        when(vendas.travar(92L)).thenReturn(Optional.of(vendaConcluida(92, vendedora, LocalDateTime.now(CLOCK).minusDays(7))));

        assertThatThrownBy(() -> service.cancelar(91L, "Motivo válido", 1L))
                .isInstanceOf(ConflictException.class).hasMessage("O prazo para cancelar esta venda expirou.");
        assertThat(service.cancelar(92L, "Motivo válido", 1L).status()).isEqualTo(StatusVenda.CANCELADA);
    }

    @Test
    void deveEstornarProdutoDesativado() {
        Venda v = vendaConcluida(91, vendedora, LocalDateTime.now(CLOCK));
        racao.desativar();
        when(vendas.travar(91L)).thenReturn(Optional.of(v));

        service.cancelar(91L, "Devolução na hora", 1L);

        assertThat(racao.getQuantidadeEstoque()).isEqualTo(12);
        assertThat(racao.isAtivo()).isFalse();
    }

    @Test
    void previaNaoDeveGravarNemTravar() {
        PreviaVendaResponse r = service.previa(req(List.of(item(12, 2), item(10, 5)), "5.00", FormaPagamento.DINHEIRO, "500.00"), 5L);

        assertThat(r.subtotal()).isEqualByComparingTo("489.80");
        assertThat(r.total()).isEqualByComparingTo("484.80");
        assertThat(r.troco()).isEqualByComparingTo("15.20");
        assertThat(r.podeFinalizar()).isFalse();
        assertThat(r.itens()).extracting(PreviaVendaResponse.Item::suficiente).containsExactly(true, false);
        assertThat(r.itens().get(1).disponivel()).isEqualTo(3);
        verify(produtos, never()).travarEmOrdem(anyCollection());
        verify(vendas, never()).saveAndFlush(any());
        verify(movimentacoes, never()).save(any());
    }

    // ------------------------------------------------------------ etapa 06: caixa e financeiro

    @Test
    void vendaSemCaixaDeveFalharAntesDeTravarProdutos() {
        org.mockito.Mockito.doThrow(new ConflictException(FinanceiroService.CAIXA_AUSENTE)).when(caixas).exigirAberto(5L);

        assertThatThrownBy(() -> service.registrar(padrao(null, FormaPagamento.PIX, null), CHAVE, 5L))
                .isInstanceOf(ConflictException.class).hasMessage(FinanceiroService.CAIXA_AUSENTE);
        verify(produtos, never()).travarEmOrdem(anyCollection());
        verify(vendas, never()).saveAndFlush(any());
        verify(financeiro, never()).registrarVenda(any(), any(), any());
    }

    @Test
    void vendaDeveRegistrarEntradaFinanceiraNoCaixa() {
        VendaResponse r = service.registrar(padrao(null, FormaPagamento.PIX, null), CHAVE, 5L).venda();

        verify(caixas).travarAberto(5L);
        verify(financeiro).registrarVenda(any(Venda.class), any(), eq(5L));
        assertThat(r.lancamentoFinanceiroId()).isEqualTo(501L);
    }

    @Test
    void cancelamentoDeveRegistrarEstornoFinanceiro() {
        Venda v = vendaConcluida(91, vendedora, LocalDateTime.now(CLOCK));
        when(vendas.travar(91L)).thenReturn(Optional.of(v));

        service.cancelar(91L, "Cliente desistiu", 1L);

        verify(caixas).exigirAberto(1L);
        verify(financeiro).registrarEstornoVenda(eq(v), any(), eq(1L), eq("Cliente desistiu"));
    }
}
