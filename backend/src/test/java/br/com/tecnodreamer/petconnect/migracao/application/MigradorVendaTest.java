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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MigradorVendaTest {

    @Mock DestinoMigracao destino;

    MigradorVenda migrador;
    ContextoFake ctx;

    @BeforeEach
    void setUp() {
        migrador = new MigradorVenda(destino);
        ctx = new ContextoFake().mapear("produto", 1, 101).mapear("cliente", 3, 30).mapear("funcionario", 2, 20);
        when(destino.inserir(eq("venda"), anyMap())).thenReturn(500L);
        when(destino.usuarioDoFuncionario(20L)).thenReturn(Optional.of(8L));
    }

    Linha venda(String total) {
        return Linha.of("id", 9, "cliente_id", 3, "funcionario_id", 2, "data_venda",
                java.time.LocalDateTime.of(2019, 3, 10, 14, 0), "valor_total", new BigDecimal(total),
                "desconto", new BigDecimal("0.00"), "forma_pagamento", "pix");
    }

    Linha item(long produto, int qtd, String preco) {
        return Linha.of("id", 1, "produto_id", produto, "quantidade", qtd, "preco_unitario", new BigDecimal(preco));
    }

    Map<String, Object> gravada() {
        ArgumentCaptor<Map<String, Object>> c = ArgumentCaptor.captor();
        verify(destino).inserir(eq("venda"), c.capture());
        return c.getValue();
    }

    @Test
    void deveCriarVendaConcluidaSemCaixa() {
        Desfecho d = migrador.migrar(venda("240.00"), ctx.legado(item(1, 2, "120.00")));

        Map<String, Object> v = gravada();
        assertThat(v).containsEntry("status", "CONCLUIDA").containsEntry("usuario_id", 8L).containsEntry("cliente_id", 30L)
                .containsEntry("forma_pagamento", "PIX");
        assertThat(v.get("caixa_id")).isNull();
        assertThat(d.situacao()).isEqualTo(Situacao.MIGRADO);
        verify(destino).inserir(eq("item_venda"), anyMap());
    }

    @Test
    void deveUsarChaveLegado() {
        migrador.migrar(venda("240.00"), ctx.legado(item(1, 2, "120.00")));

        assertThat(gravada()).containsEntry("chave_idempotencia", "LEGADO:9");
    }

    @Test
    void naoDeveBaixarEstoque() {
        migrador.migrar(venda("240.00"), ctx.legado(item(1, 2, "120.00")));

        // O migrador não conhece EstoqueService: só grava venda e itens.
        verify(destino, never()).inserir(eq("movimentacao_estoque"), any());
    }

    @Test
    void totalNaoPositivoDeveIgnorarVenda() {
        Desfecho d = migrador.migrar(venda("0.00"), ctx.legado(item(1, 1, "10.00")));

        assertThat(d).isEqualTo(Desfecho.ignorado("VENDA_TOTAL_INVALIDO"));
        verify(destino, never()).inserir(eq("venda"), any());
    }

    @Test
    void itemSemProdutoDeveIgnorarVendaInteira() {
        Desfecho d = migrador.migrar(venda("250.00"), ctx.legado(item(1, 2, "120.00"), item(999, 1, "10.00")));

        assertThat(d).isEqualTo(Desfecho.ignorado("ITEM_SEM_PRODUTO"));
        verify(destino, never()).inserir(anyString(), any());
    }

    @Test
    void somaDivergenteViraPendenciaSemAlterarTotal() {
        Desfecho d = migrador.migrar(venda("200.00"), ctx.legado(item(1, 2, "120.00")));

        assertThat(gravada().get("valor_total")).isEqualTo(new BigDecimal("200.00"));
        assertThat(d.pendencias()).containsExactly("VALOR_DIVERGENTE_ITENS");
    }
}
