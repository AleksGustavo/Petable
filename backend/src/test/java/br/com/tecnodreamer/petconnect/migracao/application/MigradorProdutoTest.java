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

import br.com.tecnodreamer.petconnect.produto.application.EstoqueService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MigradorProdutoTest {

    @Mock DestinoMigracao destino;
    @Mock EstoqueService estoque;

    MigradorProduto produtos;
    MigradorEstoqueInicial saldos;
    ContextoFake ctx = new ContextoFake().mapear("categoria_produto", 1, 11).mapear("produto", 5, 55);

    @BeforeEach
    void setUp() {
        produtos = new MigradorProduto(destino);
        saldos = new MigradorEstoqueInicial(estoque);
        when(destino.inserir(eq("produto"), anyMap())).thenReturn(55L);
    }

    Linha produto(String codigo) {
        return Linha.of("id", 5, "nome", "Ração", "categoria_id", 1, "preco_custo", new BigDecimal("80.00"),
                "preco_venda", new BigDecimal("120.00"), "estoque_minimo", 3, "codigo_barras", codigo, "ativo", 1,
                "quantidade_estoque", 10);
    }

    Map<String, Object> gravado() {
        ArgumentCaptor<Map<String, Object>> c = ArgumentCaptor.captor();
        verify(destino).inserir(eq("produto"), c.capture());
        return c.getValue();
    }

    @Test
    void produtoDeveNascerComSaldoZero() {
        produtos.migrar(produto("789"), ctx);

        assertThat(gravado()).containsEntry("quantidade_estoque", 0).containsEntry("categoria_id", 11L);
        assertThat(produtos.consulta()).doesNotContain("quantidade_estoque");
    }

    @Test
    void deveAplicarSaldoInicialViaEstoqueService() {
        Desfecho d = saldos.migrar(Linha.of("id", 5, "quantidade_estoque", 10), ctx);

        verify(estoque).ajustar(55L, 10, MigradorEstoqueInicial.MOTIVO, ContextoFake.TECNICO);
        assertThat(d.situacao()).isEqualTo(Situacao.MIGRADO);
    }

    @Test
    void saldoNegativoDeveVirarZero() {
        Desfecho d = saldos.migrar(Linha.of("id", 5, "quantidade_estoque", -3), ctx);

        verify(estoque, never()).ajustar(any(), org.mockito.ArgumentMatchers.anyInt(), any(), any());
        assertThat(d.pendencias()).containsExactly("SALDO_NEGATIVO");
    }

    @Test
    void codigoBarrasDuplicadoDeveVirarNull() {
        when(destino.codigoBarrasExiste("789")).thenReturn(true);

        Desfecho d = produtos.migrar(produto("789"), ctx);

        assertThat(gravado().get("codigo_barras")).isNull();
        assertThat(d.pendencias()).containsExactly("CODIGO_BARRAS_DUPLICADO");
    }
}
