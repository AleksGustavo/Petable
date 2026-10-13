package br.com.tecnodreamer.petconnect.migracao.application;

import br.com.tecnodreamer.petconnect.migracao.infrastructure.DestinoMigracao;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Compra histórica com seus itens (DA-27, DA-28): sem ENTRADA_COMPRA (o saldo já foi
 * ajustado) e sem conta a pagar — o legado não prova dívida em aberto; o backfill
 * financeiro também ignora compras vindas daqui.
 */
@Component
@Order(180)
public class MigradorCompra implements Migrador {

    private final DestinoMigracao destino;

    public MigradorCompra(DestinoMigracao destino) {
        this.destino = destino;
    }

    @Override
    public String entidade() {
        return "compra";
    }

    @Override
    public String consulta() {
        return """
                SELECT id, fornecedor_id, funcionario_id, data_compra, data_entrega, valor_total, status,
                       forma_pagamento, observacoes
                  FROM compra WHERE id > :ultimo ORDER BY id LIMIT :lote
                """;
    }

    @Override
    public Desfecho migrar(Linha l, Contexto ctx) {
        Optional<Long> fornecedor = ctx.idNovo("fornecedor", l.longo("fornecedor_id"));
        if (fornecedor.isEmpty()) {
            return Desfecho.ignorado("FORNECEDOR_NAO_MIGRADO");
        }
        BigDecimal total = l.decimal("valor_total");
        if (total == null || total.signum() < 0 || l.data("data_compra") == null) {
            return Desfecho.ignorado("DADOS_OBRIGATORIOS_AUSENTES");
        }
        List<String> pend = new ArrayList<>();
        Long funcionario = opcional(ctx, "funcionario", l.longo("funcionario_id"), pend, "FUNCIONARIO_NAO_MIGRADO");
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("fornecedor_id", fornecedor.get());
        v.put("funcionario_id", funcionario);
        v.put("data_compra", l.data("data_compra"));
        v.put("data_entrega", l.data("data_entrega"));
        v.put("valor_total", total);
        v.put("status", ConversorLegado.statusCompra(l.texto("status")).anotar(pend).name());
        v.put("forma_pagamento", TextoNormalizado.limitar(l.texto("forma_pagamento"), 50));
        v.put("observacoes", l.texto("observacoes"));
        Long compraId = destino.inserir("compra", v);
        for (Linha item : ctx.consultarLegado(
                "SELECT id, produto_id, quantidade, preco_custo_unitario FROM item_compra WHERE compra_id = :id ORDER BY id",
                Map.of("id", l.id()))) {
            Optional<Long> produto = ctx.idNovo("produto", item.longo("produto_id"));
            Integer qtd = item.inteiro("quantidade");
            BigDecimal custo = item.decimal("preco_custo_unitario");
            if (produto.isEmpty() || qtd == null || qtd <= 0 || custo == null || custo.signum() < 0) {
                pend.add("ITEM_COMPRA_IGNORADO");
                continue;
            }
            Map<String, Object> i = new LinkedHashMap<>();
            i.put("compra_id", compraId);
            i.put("produto_id", produto.get());
            i.put("quantidade", qtd);
            i.put("preco_custo_unitario", custo);
            destino.inserir("item_compra", i);
        }
        return Desfecho.migrado(compraId, pend);
    }

    static Long opcional(Contexto ctx, String entidade, Long idLegado, List<String> pend, String codigo) {
        if (idLegado == null) {
            return null;
        }
        Long novo = ctx.idNovo(entidade, idLegado).orElse(null);
        if (novo == null) {
            pend.add(codigo);
        }
        return novo;
    }
}
