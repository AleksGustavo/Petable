package br.com.tecnodreamer.petconnect.financeiro.application;

import br.com.tecnodreamer.petconnect.agendamento.domain.ServicoRealizado;
import br.com.tecnodreamer.petconnect.compra.domain.Compra;
import br.com.tecnodreamer.petconnect.financeiro.domain.Caixa;
import br.com.tecnodreamer.petconnect.financeiro.domain.CategoriaLancamento;
import br.com.tecnodreamer.petconnect.financeiro.domain.ContaPagar;
import br.com.tecnodreamer.petconnect.financeiro.domain.Lancamento;
import br.com.tecnodreamer.petconnect.financeiro.domain.StatusConta;
import br.com.tecnodreamer.petconnect.financeiro.domain.TipoLancamento;
import br.com.tecnodreamer.petconnect.financeiro.infrastructure.CaixaRepository;
import br.com.tecnodreamer.petconnect.financeiro.infrastructure.ContaPagarRepository;
import br.com.tecnodreamer.petconnect.financeiro.infrastructure.LancamentoRepository;
import br.com.tecnodreamer.petconnect.financeiro.web.ContaPagarResponse;
import br.com.tecnodreamer.petconnect.financeiro.web.LancamentoManualRequest;
import br.com.tecnodreamer.petconnect.financeiro.web.LancamentoResponse;
import br.com.tecnodreamer.petconnect.servico.domain.Servico;
import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;
import br.com.tecnodreamer.petconnect.shared.error.ConflictException;
import br.com.tecnodreamer.petconnect.usuario.domain.Usuario;
import br.com.tecnodreamer.petconnect.usuario.infrastructure.UsuarioRepository;
import br.com.tecnodreamer.petconnect.venda.domain.FormaPagamento;
import br.com.tecnodreamer.petconnect.venda.domain.Venda;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

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
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FinanceiroServiceTest {

    static final String CHAVE = "0b8f9a52-5f0e-4d0e-9d1c-6e7b2a1c3d4e";

    @Mock LancamentoRepository lancamentos;
    @Mock ContaPagarRepository contas;
    @Mock CaixaRepository caixas;
    @Mock UsuarioRepository usuarios;
    @Mock AuditoriaService auditoria;

    FinanceiroService service;
    final Usuario admin = usuario(1, "admin", ADMIN, "h");
    Caixa caixa;
    /** Simula a UNIQUE de chave_origem do banco. */
    final Set<String> chaves = new HashSet<>();
    final AtomicLong ids = new AtomicLong(500);

    @BeforeEach
    void setUp() {
        service = new FinanceiroService(lancamentos, contas, caixas, usuarios, auditoria, CLOCK);
        caixa = new Caixa(admin, new BigDecimal("200.00"), LocalDateTime.now(CLOCK));
        ReflectionTestUtils.setField(caixa, "id", 18L);
        when(usuarios.findById(1L)).thenReturn(Optional.of(admin));
        when(caixas.travarAbertoDoUsuario(1L)).thenReturn(Optional.of(caixa));
        when(lancamentos.saveAndFlush(any())).thenAnswer(inv -> {
            Lancamento l = inv.getArgument(0);
            if (!chaves.add(l.getChaveOrigem())) {
                throw new DataIntegrityViolationException("uk_mov_fin_chave");
            }
            ReflectionTestUtils.setField(l, "id", ids.incrementAndGet());
            return l;
        });
        when(contas.saveAndFlush(any())).thenAnswer(inv -> {
            ContaPagar c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 40L);
            return c;
        });
    }

    private Venda venda(long id) {
        Venda v = new Venda(null, null, admin, LocalDateTime.now(CLOCK), FormaPagamento.PIX, null, CHAVE);
        v.adicionarItem(produto(10, 10, "20.00", 1), 2);
        v.fechar(new BigDecimal("5.00"), null);
        ReflectionTestUtils.setField(v, "id", id);
        return v;
    }

    private Lancamento capturar() {
        ArgumentCaptor<Lancamento> captor = ArgumentCaptor.forClass(Lancamento.class);
        verify(lancamentos, org.mockito.Mockito.atLeastOnce()).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private Lancamento manualExistente(long id, TipoLancamento tipo) {
        Lancamento l = new Lancamento(tipo, CategoriaLancamento.DESPESA_OPERACIONAL, "Material", new BigDecimal("89.90"),
                FormaPagamento.DINHEIRO, HOJE, admin, caixa, Lancamento.Origem.NENHUMA, "MANUAL:" + CHAVE, null, null, null);
        ReflectionTestUtils.setField(l, "id", id);
        return l;
    }

    @Test
    void deveRegistrarEntradaDeVenda() {
        Lancamento l = service.registrarVenda(venda(91), caixa, 1L);

        assertThat(l.getTipo()).isEqualTo(TipoLancamento.ENTRADA);
        assertThat(l.getCategoria()).isEqualTo(CategoriaLancamento.VENDA_PRODUTO);
        assertThat(l.getValor()).isEqualByComparingTo("194.80");
        assertThat(l.getFormaPagamento()).isEqualTo(FormaPagamento.PIX);
        assertThat(l.getChaveOrigem()).isEqualTo("VENDA:91");
        assertThat(l.getVendaId()).isEqualTo(91L);
        assertThat(l.getCaixa()).isSameAs(caixa);
        assertThat(l.getDataMovimentacao()).isEqualTo(HOJE);
        verify(auditoria).registrar(eq("LANCAMENTO_CRIADO"), eq("lancamento"), any(), anyMap());
    }

    @Test
    void deveImpedirDuplicidadeDaMesmaVenda() {
        service.registrarVenda(venda(91), caixa, 1L);

        assertThatThrownBy(() -> service.registrarVenda(venda(91), caixa, 1L)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveRegistrarEstornoDeVenda() {
        Lancamento original = service.registrarVenda(venda(91), caixa, 1L);
        when(lancamentos.findByChaveOrigem("VENDA:91")).thenReturn(Optional.of(original));

        Lancamento estorno = service.registrarEstornoVenda(venda(91), caixa, 1L, "Cliente desistiu").orElseThrow();

        assertThat(estorno.getTipo()).isEqualTo(TipoLancamento.SAIDA);
        assertThat(estorno.getCategoria()).isEqualTo(CategoriaLancamento.VENDA_PRODUTO);
        assertThat(estorno.getValor()).isEqualByComparingTo(original.getValor());
        assertThat(estorno.getFormaPagamento()).isEqualTo(FormaPagamento.PIX);
        assertThat(estorno.getChaveOrigem()).isEqualTo("VENDA_ESTORNO:91");
    }

    @Test
    void deveVincularEstornoAoOriginal() {
        Lancamento original = service.registrarVenda(venda(91), caixa, 1L);
        when(lancamentos.findByChaveOrigem("VENDA:91")).thenReturn(Optional.of(original));

        Lancamento estorno = service.registrarEstornoVenda(venda(91), caixa, 1L, "Cliente desistiu").orElseThrow();

        assertThat(estorno.getEstornoDeId()).isEqualTo(original.getId());
        assertThat(estorno.getMotivoEstorno()).isEqualTo("Cliente desistiu");
        // Venda anterior à V8 sem lançamento: nada a estornar.
        assertThat(service.registrarEstornoVenda(venda(92), caixa, 1L, "Sem original")).isEmpty();
    }

    @Test
    void deveRegistrarReceitaDeServico() {
        ServicoRealizado s = mock(ServicoRealizado.class);
        Servico banho = mock(Servico.class);
        when(banho.getNome()).thenReturn("Banho");
        when(s.getId()).thenReturn(7L);
        when(s.getServico()).thenReturn(banho);
        when(s.getValor()).thenReturn(new BigDecimal("55.00"));

        Lancamento l = service.registrarServico(s, FormaPagamento.DINHEIRO, caixa, 1L).orElseThrow();

        assertThat(l.getTipo()).isEqualTo(TipoLancamento.ENTRADA);
        assertThat(l.getCategoria()).isEqualTo(CategoriaLancamento.SERVICO);
        assertThat(l.getServicoRealizadoId()).isEqualTo(7L);
        assertThat(l.getChaveOrigem()).isEqualTo("SERVICO:7");
        // Serviço de valor zero não gera receita.
        when(s.getValor()).thenReturn(BigDecimal.ZERO);
        assertThat(service.registrarServico(s, FormaPagamento.PIX, caixa, 1L)).isEmpty();
    }

    @Test
    void deveCriarContaPagarDaCompra() {
        Compra c = compra(12, fornecedor(4, true), HOJE);
        c.adicionarItem(produto(10, 0, null, 1), 10, new BigDecimal("125.00"));

        ContaPagar conta = service.criarContaPagar(c, HOJE.plusDays(30));

        assertThat(conta.getStatus()).isEqualTo(StatusConta.ABERTA);
        assertThat(conta.getValorOriginal()).isEqualByComparingTo("1250.00");
        assertThat(conta.getSaldo()).isEqualByComparingTo("1250.00");
        assertThat(conta.getDataVencimento()).isEqualTo(HOJE.plusDays(30));
        verify(lancamentos, never()).saveAndFlush(any());
        verify(auditoria).registrar(eq("CONTA_PAGAR_CRIADA"), eq("conta_pagar"), eq(40L), anyMap());
    }

    @Test
    void deveImpedirDuasContasDaMesmaCompra() {
        Compra c = compra(12, fornecedor(4, true), HOJE);
        when(contas.findByCompraId(12L)).thenReturn(Optional.of(mock(ContaPagar.class)));

        assertThatThrownBy(() -> service.criarContaPagar(c, HOJE)).isInstanceOf(ConflictException.class);
        verify(contas, never()).saveAndFlush(any());
    }

    private ContaPagar conta() {
        Compra c = compra(12, fornecedor(4, true), HOJE);
        ContaPagar conta = new ContaPagar(c, "Compra #12", new BigDecimal("1250.00"), HOJE.plusDays(10));
        ReflectionTestUtils.setField(conta, "id", 40L);
        when(contas.travar(40L)).thenReturn(Optional.of(conta));
        return conta;
    }

    @Test
    void devePagarContaIntegralmente() {
        ContaPagar conta = conta();

        ContaPagarResponse r = service.pagar(40L, FormaPagamento.PIX, 1L);

        assertThat(r.status()).isEqualTo(StatusConta.PAGA);
        assertThat(r.saldo()).isEqualByComparingTo("0.00");
        assertThat(conta.getFormaPagamento()).isEqualTo(FormaPagamento.PIX);
        Lancamento saida = capturar();
        assertThat(saida.getTipo()).isEqualTo(TipoLancamento.SAIDA);
        assertThat(saida.getCategoria()).isEqualTo(CategoriaLancamento.COMPRA_MERCADORIA);
        assertThat(saida.getValor()).isEqualByComparingTo("1250.00");
        assertThat(saida.getContaPagarId()).isEqualTo(40L);
        assertThat(saida.getCompraId()).isEqualTo(12L);
        assertThat(r.lancamentoId()).isEqualTo(saida.getId());
    }

    @Test
    void deveRejeitarContaJaPaga() {
        ContaPagar conta = conta();
        conta.pagar(FormaPagamento.PIX, admin, LocalDateTime.now(CLOCK));

        assertThatThrownBy(() -> service.pagar(40L, FormaPagamento.PIX, 1L))
                .isInstanceOf(ConflictException.class).hasMessage("Esta conta já foi paga.");
        verify(lancamentos, never()).saveAndFlush(any());
    }

    private static LancamentoManualRequest manual(CategoriaLancamento categoria, String valor) {
        return new LancamentoManualRequest(TipoLancamento.SAIDA, categoria, " Material de limpeza ", new BigDecimal(valor),
                FormaPagamento.DINHEIRO, "Higienização");
    }

    @Test
    void deveCriarLancamentoManual() {
        ResultadoLancamento r = service.lancarManual(manual(CategoriaLancamento.DESPESA_OPERACIONAL, "89.90"), CHAVE, 1L);

        assertThat(r.repetido()).isFalse();
        assertThat(r.lancamento().descricao()).isEqualTo("Material de limpeza");
        assertThat(r.lancamento().caixaId()).isEqualTo(18L);
        assertThat(capturar().getChaveOrigem()).isEqualTo("MANUAL:" + CHAVE);
    }

    @Test
    void deveRejeitarCategoriaAutomaticaManual() {
        assertThatThrownBy(() -> service.lancarManual(manual(CategoriaLancamento.VENDA_PRODUTO, "10.00"), CHAVE, 1L))
                .isInstanceOf(CampoInvalidoException.class).extracting("campo").isEqualTo("categoria");
        assertThatThrownBy(() -> service.lancarManual(manual(CategoriaLancamento.OUTROS, "10.00"), "nao-uuid", 1L))
                .isInstanceOf(CampoInvalidoException.class).extracting("campo").isEqualTo("idempotencyKey");
    }

    @Test
    void deveSerIdempotenteNoLancamentoManual() {
        when(lancamentos.findByChaveOrigem("MANUAL:" + CHAVE)).thenReturn(Optional.of(manualExistente(520, TipoLancamento.SAIDA)));

        ResultadoLancamento r = service.lancarManual(manual(CategoriaLancamento.DESPESA_OPERACIONAL, "89.90"), CHAVE, 1L);

        assertThat(r.repetido()).isTrue();
        assertThat(r.lancamento().id()).isEqualTo(520L);
        verify(lancamentos, never()).saveAndFlush(any());
    }

    @Test
    void deveEstornarLancamentoManual() {
        when(lancamentos.findById(520L)).thenReturn(Optional.of(manualExistente(520, TipoLancamento.SAIDA)));

        LancamentoResponse r = service.estornar(520L, "Despesa lançada em duplicidade", 1L);

        assertThat(r.tipo()).isEqualTo(TipoLancamento.ENTRADA);
        assertThat(r.estornoDeId()).isEqualTo(520L);
        assertThat(r.motivoEstorno()).isEqualTo("Despesa lançada em duplicidade");
        assertThat(r.valor()).isEqualByComparingTo("89.90");
        verify(auditoria).registrar(eq("LANCAMENTO_ESTORNADO"), eq("lancamento"), eq(520L), anyMap());
    }

    @Test
    void deveRejeitarSegundoEstorno() {
        when(lancamentos.findById(520L)).thenReturn(Optional.of(manualExistente(520, TipoLancamento.SAIDA)));
        when(lancamentos.existsByEstornoDeId(520L)).thenReturn(true);

        assertThatThrownBy(() -> service.estornar(520L, "Motivo válido", 1L))
                .isInstanceOf(ConflictException.class).hasMessage("Este lançamento já foi estornado.");
        // Lançamento automático não é estornado por aqui.
        Lancamento automatico = service.registrarVenda(venda(91), caixa, 1L);
        when(lancamentos.findById(automatico.getId())).thenReturn(Optional.of(automatico));
        assertThatThrownBy(() -> service.estornar(automatico.getId(), "Motivo válido", 1L)).isInstanceOf(ConflictException.class);
    }

    @Test
    void deveUsarBigDecimalDuasCasas() {
        service.lancarManual(manual(CategoriaLancamento.OUTROS, "10.5"), CHAVE, 1L);

        Lancamento l = capturar();
        assertThat(l.getValor()).isEqualTo(new BigDecimal("10.50"));
        assertThat(l.getValor().scale()).isEqualTo(2);
        assertThat(FinanceiroService.decimal(new BigDecimal("0.105"))).isEqualTo(new BigDecimal("0.11"));
        verify(auditoria, never()).registrar(eq("LANCAMENTO_ESTORNADO"), anyString(), any(), anyMap());
    }
}
