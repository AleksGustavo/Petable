package br.com.tecnodreamer.petconnect.migracao.application;

import br.com.tecnodreamer.petconnect.migracao.infrastructure.DestinoMigracao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.tecnodreamer.petconnect.financeiro.application.FinanceiroService;
import br.com.tecnodreamer.petconnect.financeiro.domain.CategoriaLancamento;
import br.com.tecnodreamer.petconnect.financeiro.domain.Lancamento;
import br.com.tecnodreamer.petconnect.financeiro.domain.TipoLancamento;

import java.time.LocalDate;

import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MigradorFinanceiroTest {

    @Mock FinanceiroService financeiro;

    MigradorFinanceiro migrador;
    ContextoFake ctx = new ContextoFake();

    @BeforeEach
    void setUp() {
        migrador = new MigradorFinanceiro(financeiro);
        Lancamento l = mock(Lancamento.class);
        when(l.getId()).thenReturn(77L);
        when(financeiro.registrarLegado(any(), any(), any(), any(), any(), any(), any())).thenReturn(l);
    }

    Linha mov(String tipo, String categoria) {
        return Linha.of("id", 4, "tipo", tipo, "descricao", "Conta de luz", "valor", new BigDecimal("150.00"),
                "data_movimentacao", LocalDate.of(2019, 3, 15), "categoria", categoria);
    }

    @Test
    void deveIgnorarFinanceiroDeVendaParaBackfill() {
        assertThat(migrador.migrar(mov("entrada", "venda_produto"), ctx)).isEqualTo(Desfecho.ignorado("REGENERADO_PELO_BACKFILL"));
        verify(financeiro, never()).registrarLegado(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void deveIgnorarFinanceiroDeServicoParaBackfill() {
        assertThat(migrador.migrar(mov("entrada", "servico"), ctx).situacao()).isEqualTo(Situacao.IGNORADO);
    }

    @Test
    void deveMigrarDespesaManualLegada() {
        Desfecho d = migrador.migrar(mov("saida", "despesa_operacional"), ctx);

        verify(financeiro).registrarLegado(eq(TipoLancamento.SAIDA), eq(CategoriaLancamento.DESPESA_OPERACIONAL),
                eq("Conta de luz"), eq(new BigDecimal("150.00")), eq(LocalDate.of(2019, 3, 15)), eq(4L), anyString());
        assertThat(d).isEqualTo(Desfecho.migrado(77L));
    }

    @Test
    void deveUsarChaveOrigemLegado() {
        // A chave LEGADO:<id> é montada pelo FinanceiroService a partir do id legado (4).
        migrador.migrar(mov("saida", null), ctx);

        verify(financeiro).registrarLegado(any(), eq(CategoriaLancamento.OUTROS), any(), any(), any(), eq(4L), any());
    }

    @Test
    void tipoDesconhecidoDeveSerIgnorado() {
        assertThat(migrador.migrar(mov("estorno", "outros"), ctx)).isEqualTo(Desfecho.ignorado("TIPO_FINANCEIRO_DESCONHECIDO"));
    }
}
