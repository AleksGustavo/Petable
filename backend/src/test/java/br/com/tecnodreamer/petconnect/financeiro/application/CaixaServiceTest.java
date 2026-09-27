package br.com.tecnodreamer.petconnect.financeiro.application;

import br.com.tecnodreamer.petconnect.financeiro.domain.Caixa;
import br.com.tecnodreamer.petconnect.financeiro.domain.CaixaMovimento;
import br.com.tecnodreamer.petconnect.financeiro.domain.StatusCaixa;
import br.com.tecnodreamer.petconnect.financeiro.domain.TipoMovimentoCaixa;
import br.com.tecnodreamer.petconnect.financeiro.infrastructure.CaixaMovimentoRepository;
import br.com.tecnodreamer.petconnect.financeiro.infrastructure.CaixaRepository;
import br.com.tecnodreamer.petconnect.financeiro.infrastructure.LancamentoRepository;
import br.com.tecnodreamer.petconnect.financeiro.web.CaixaResponse;
import br.com.tecnodreamer.petconnect.financeiro.web.PreviaFechamentoResponse;
import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;
import br.com.tecnodreamer.petconnect.shared.error.ConflictException;
import br.com.tecnodreamer.petconnect.usuario.domain.Usuario;
import br.com.tecnodreamer.petconnect.usuario.infrastructure.UsuarioRepository;
import br.com.tecnodreamer.petconnect.venda.domain.FormaPagamento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static br.com.tecnodreamer.petconnect.pet.PetFixtures.CLOCK;
import static br.com.tecnodreamer.petconnect.usuario.UsuarioFixtures.VENDAS;
import static br.com.tecnodreamer.petconnect.usuario.UsuarioFixtures.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CaixaServiceTest {

    @Mock CaixaRepository caixas;
    @Mock CaixaMovimentoRepository movimentos;
    @Mock LancamentoRepository lancamentos;
    @Mock FinanceiroService financeiro;
    @Mock UsuarioRepository usuarios;
    @Mock AuditoriaService auditoria;

    CaixaService service;
    final Usuario joana = usuario(5, "joana", VENDAS, "h");
    Caixa caixa;
    final List<Object[]> saldos = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new CaixaService(caixas, movimentos, lancamentos, financeiro, usuarios, auditoria, CLOCK);
        caixa = new Caixa(joana, new BigDecimal("200.00"), LocalDateTime.now(CLOCK));
        ReflectionTestUtils.setField(caixa, "id", 18L);
        when(usuarios.findById(5L)).thenReturn(Optional.of(joana));
        when(caixas.findByUsuarioIdAndStatus(5L, StatusCaixa.ABERTO)).thenReturn(Optional.of(caixa));
        when(caixas.travarAbertoDoUsuario(5L)).thenReturn(Optional.of(caixa));
        when(caixas.saveAndFlush(any())).thenAnswer(inv -> {
            Caixa c = inv.getArgument(0);
            if (c.getId() == null) {
                ReflectionTestUtils.setField(c, "id", 19L);
            }
            return c;
        });
        when(movimentos.saveAndFlush(any())).thenAnswer(inv -> {
            CaixaMovimento m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "id", 3L);
            return m;
        });
        when(lancamentos.saldoPorFormaDoCaixa(18L)).thenReturn(saldos);
    }

    /** §12: venda 500 + serviço 180 + suprimento 100 − sangria 200 − despesa 50, tudo em dinheiro = 530. */
    private void movimentoDoExemplo() {
        saldos.add(new Object[] {"DINHEIRO", new BigDecimal("530.00")});
    }

    private static Map<FormaPagamento, BigDecimal> contado(String dinheiro, String pix, String credito) {
        Map<FormaPagamento, BigDecimal> m = new EnumMap<>(FormaPagamento.class);
        for (FormaPagamento f : FormaPagamento.values()) {
            m.put(f, BigDecimal.ZERO);
        }
        m.put(FormaPagamento.DINHEIRO, new BigDecimal(dinheiro));
        m.put(FormaPagamento.PIX, new BigDecimal(pix));
        m.put(FormaPagamento.CARTAO_CREDITO, new BigDecimal(credito));
        return m;
    }

    @Test
    void deveAbrirCaixa() {
        when(caixas.findByUsuarioIdAndStatus(5L, StatusCaixa.ABERTO)).thenReturn(Optional.empty());

        CaixaResponse r = service.abrir(5L, new BigDecimal("200"));

        assertThat(r.status()).isEqualTo(StatusCaixa.ABERTO);
        assertThat(r.fundoAbertura()).isEqualTo(new BigDecimal("200.00"));
        assertThat(r.abertoEm()).isEqualTo(LocalDateTime.now(CLOCK));
        verify(auditoria).registrar(eq("CAIXA_ABERTO"), eq("caixa"), eq(19L), anyMap());
    }

    @Test
    void deveRejeitarSegundoCaixaAberto() {
        assertThatThrownBy(() -> service.abrir(5L, BigDecimal.ZERO))
                .isInstanceOf(ConflictException.class).hasMessage(CaixaService.JA_ABERTO);
        // Corrida: a UNIQUE da coluna gerada barra a segunda abertura.
        when(caixas.findByUsuarioIdAndStatus(5L, StatusCaixa.ABERTO)).thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("uk_caixa_usuario_aberto")).when(caixas).saveAndFlush(any());
        assertThatThrownBy(() -> service.abrir(5L, BigDecimal.ZERO))
                .isInstanceOf(ConflictException.class).hasMessage(CaixaService.JA_ABERTO);
    }

    @Test
    void deveRegistrarSuprimento() {
        CaixaResponse.Movimento m = service.suprimento(5L, new BigDecimal("100.00"), " Reforço de troco ");

        assertThat(m.tipo()).isEqualTo(TipoMovimentoCaixa.SUPRIMENTO);
        assertThat(m.motivo()).isEqualTo("Reforço de troco");
        verify(financeiro).registrarMovimentoCaixa(any(CaixaMovimento.class), eq(5L));
        verify(auditoria).registrar(eq("CAIXA_SUPRIMENTO"), eq("caixa"), eq(18L), anyMap());
    }

    @Test
    void deveRegistrarSangria() {
        movimentoDoExemplo();

        CaixaResponse.Movimento m = service.sangria(5L, new BigDecimal("730.00"), "Retirada preventiva");

        assertThat(m.tipo()).isEqualTo(TipoMovimentoCaixa.SANGRIA);
        verify(financeiro).registrarMovimentoCaixa(any(CaixaMovimento.class), eq(5L));
        verify(auditoria).registrar(eq("CAIXA_SANGRIA"), eq("caixa"), eq(18L), anyMap());
    }

    @Test
    void deveRejeitarSangriaExcedente() {
        movimentoDoExemplo();

        assertThatThrownBy(() -> service.sangria(5L, new BigDecimal("730.01"), "Retirada preventiva"))
                .isInstanceOf(ConflictException.class).hasMessage(CaixaService.SANGRIA_EXCEDE);
        verify(movimentos, never()).saveAndFlush(any());
        verify(financeiro, never()).registrarMovimentoCaixa(any(), any());
    }

    @Test
    void deveCalcularEsperadoDinheiro() {
        movimentoDoExemplo();

        PreviaFechamentoResponse p = service.previaFechamento(5L);

        // 200 + 500 + 180 + 100 − 200 − 50 = 730
        assertThat(p.esperado().get(FormaPagamento.DINHEIRO)).isEqualByComparingTo("730.00");
    }

    @Test
    void deveCalcularEsperadoPorForma() {
        movimentoDoExemplo();
        saldos.add(new Object[] {"PIX", new BigDecimal("980.00")});
        saldos.add(new Object[] {"CARTAO_CREDITO", new BigDecimal("1200.00")});

        PreviaFechamentoResponse p = service.previaFechamento(5L);

        assertThat(p.esperado().get(FormaPagamento.PIX)).isEqualByComparingTo("980.00");
        assertThat(p.esperado().get(FormaPagamento.CARTAO_CREDITO)).isEqualByComparingTo("1200.00");
        assertThat(p.esperado().get(FormaPagamento.TRANSFERENCIA)).isEqualByComparingTo("0.00");
        assertThat(p.totalEsperado()).isEqualByComparingTo("2910.00");
    }

    @Test
    void deveCalcularDiferenca() {
        movimentoDoExemplo();

        CaixaResponse r = service.fechar(5L, contado("725.00", "0", "0"), "Diferença de R$ 5,00 na contagem");

        assertThat(r.esperado().get(FormaPagamento.DINHEIRO)).isEqualByComparingTo("730.00");
        assertThat(r.contado().get(FormaPagamento.DINHEIRO)).isEqualByComparingTo("725.00");
        assertThat(r.diferenca().get(FormaPagamento.DINHEIRO)).isEqualByComparingTo("-5.00");
        assertThat(r.diferencaTotal()).isEqualByComparingTo("-5.00");
        verify(auditoria).registrar(eq("CAIXA_FECHADO"), eq("caixa"), eq(18L),
                org.mockito.ArgumentMatchers.argThat(d -> new BigDecimal("-5.00").compareTo((BigDecimal) d.get("diferenca")) == 0));
    }

    @Test
    void deveExigirMotivoParaDiferenca() {
        movimentoDoExemplo();

        assertThatThrownBy(() -> service.fechar(5L, contado("725.00", "0", "0"), null))
                .isInstanceOf(CampoInvalidoException.class).extracting("campo").isEqualTo("motivoDiferenca");
        assertThat(caixa.isAberto()).isTrue();
    }

    @Test
    void deveFecharCaixa() {
        movimentoDoExemplo();

        CaixaResponse r = service.fechar(5L, contado("730.00", "0", "0"), null);

        assertThat(r.status()).isEqualTo(StatusCaixa.FECHADO);
        assertThat(r.fechadoEm()).isEqualTo(LocalDateTime.now(CLOCK));
        assertThat(r.diferencaTotal()).isEqualByComparingTo("0.00");
        assertThat(r.motivoDiferenca()).isNull();
    }

    @Test
    void deveFecharComLiquidoNegativoEmFormaEletronicaMasNaoEmDinheiro() {
        // Venda de 39,90 por Pix e conta de 50,00 paga por Pix no mesmo caixa: esperado Pix = −10,10.
        saldos.add(new Object[] {"PIX", new BigDecimal("-10.10")});
        Map<FormaPagamento, BigDecimal> contado = contado("200.00", "-10.10", "0");

        CaixaResponse r = service.fechar(5L, contado, null);

        assertThat(r.diferencaTotal()).isEqualByComparingTo("0.00");
        caixa = new Caixa(joana, BigDecimal.ZERO, LocalDateTime.now(CLOCK));
        when(caixas.travarAbertoDoUsuario(5L)).thenReturn(Optional.of(caixa));
        assertThatThrownBy(() -> service.fechar(5L, contado("-1.00", "0", "0"), "Motivo qualquer"))
                .isInstanceOf(CampoInvalidoException.class).extracting("campo").isEqualTo("contado.DINHEIRO");
    }

    @Test
    void deveRejeitarSegundoFechamento() {
        service.fechar(5L, contado("200.00", "0", "0"), null);
        when(caixas.travarAbertoDoUsuario(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.fechar(5L, contado("200.00", "0", "0"), null)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> caixa.fechar(Map.of(), Map.of(), null, LocalDateTime.now(CLOCK)))
                .isInstanceOf(ConflictException.class).hasMessage(Caixa.JA_FECHADO);
    }
}
