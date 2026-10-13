package br.com.tecnodreamer.petconnect.migracao.application;

import br.com.tecnodreamer.petconnect.migracao.infrastructure.DestinoMigracao;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Venda histórica com os itens (DA-25, DA-26): CONCLUIDA, chave {@code LEGADO:<id>},
 * sem caixa e SEM baixa de estoque. Total ≤ 0 ou qualquer item sem produto válido →
 * a venda inteira é ignorada. Soma dos itens diferente do total vira pendência, mas o
 * valor histórico não é alterado.
 */
@Component
@Order(210)
public class MigradorVenda implements Migrador {

    private final DestinoMigracao destino;

    public MigradorVenda(DestinoMigracao destino) {
        this.destino = destino;
    }

    @Override
    public String entidade() {
        return "venda";
    }

    @Override
    public String consulta() {
        return """
                SELECT id, cliente_id, funcionario_id, data_venda, valor_total, desconto, forma_pagamento,
                       observacoes, created_at
                  FROM venda WHERE id > :ultimo ORDER BY id LIMIT :lote
                """;
    }

    @Override
    public Desfecho migrar(Linha l, Contexto ctx) {
        BigDecimal total = l.decimal("valor_total");
        if (total == null || total.signum() <= 0) {
            return Desfecho.ignorado("VENDA_TOTAL_INVALIDO");
        }
        List<Linha> itens = ctx.consultarLegado(
                "SELECT id, produto_id, quantidade, preco_unitario FROM item_venda WHERE venda_id = :id ORDER BY id",
                Map.of("id", l.id()));
        if (itens.isEmpty()) {
            return Desfecho.ignorado("VENDA_SEM_ITENS");
        }
        List<Map<String, Object>> novos = new ArrayList<>();
        BigDecimal soma = BigDecimal.ZERO.setScale(2);
        for (Linha item : itens) {
            Optional<Long> produto = ctx.idNovo("produto", item.longo("produto_id"));
            if (produto.isEmpty()) {
                return Desfecho.ignorado("ITEM_SEM_PRODUTO");
            }
            Integer qtd = item.inteiro("quantidade");
            BigDecimal preco = item.decimal("preco_unitario");
            if (qtd == null || qtd <= 0 || preco == null || preco.signum() < 0) {
                return Desfecho.ignorado("ITEM_INVALIDO");
            }
            soma = soma.add(preco.multiply(BigDecimal.valueOf(qtd))).setScale(2, RoundingMode.HALF_UP);
            Map<String, Object> i = new LinkedHashMap<>();
            i.put("produto_id", produto.get());
            i.put("quantidade", qtd);
            i.put("preco_unitario", preco);
            novos.add(i);
        }
        List<String> pend = new ArrayList<>();
        BigDecimal desconto = l.decimal("desconto");
        if (desconto == null) {
            desconto = BigDecimal.ZERO.setScale(2);
        } else if (desconto.signum() < 0) {
            pend.add("DESCONTO_INVALIDO");
            desconto = BigDecimal.ZERO.setScale(2);
        }
        if (soma.subtract(desconto).compareTo(total) != 0) {
            pend.add("VALOR_DIVERGENTE_ITENS");
        }
        Long funcionario = MigradorCompra.opcional(ctx, "funcionario", l.longo("funcionario_id"), pend,
                "FUNCIONARIO_NAO_MIGRADO");
        Long usuario = funcionario == null ? ctx.usuarioTecnicoId()
                : destino.usuarioDoFuncionario(funcionario).orElse(ctx.usuarioTecnicoId());
        LocalDateTime data = l.dataHora("data_venda");
        if (data == null) {
            pend.add("DATA_VENDA_AUSENTE");
            data = l.dataHora("created_at");
        }
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("cliente_id", MigradorCompra.opcional(ctx, "cliente", l.longo("cliente_id"), pend, "CLIENTE_NAO_MIGRADO"));
        v.put("funcionario_id", funcionario);
        v.put("usuario_id", usuario);
        v.put("caixa_id", null);
        v.put("data_venda", data);
        v.put("valor_total", total);
        v.put("desconto", desconto);
        v.put("forma_pagamento", ConversorLegado.formaPagamento(l.texto("forma_pagamento")).anotar(pend).name());
        v.put("status", "CONCLUIDA");
        v.put("chave_idempotencia", "LEGADO:" + l.id());
        v.put("observacoes", l.texto("observacoes"));
        Long vendaId = destino.inserir("venda", v);
        for (Map<String, Object> i : novos) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("venda_id", vendaId);
            item.putAll(i);
            destino.inserir("item_venda", item);
        }
        return Desfecho.migrado(vendaId, pend);
    }
}
