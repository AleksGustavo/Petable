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

/** Produto nasce com estoque 0 (DA-22); o saldo legado entra no passo seguinte, pelo EstoqueService. */
@Component
@Order(160)
public class MigradorProduto implements Migrador {

    private final DestinoMigracao destino;

    public MigradorProduto(DestinoMigracao destino) {
        this.destino = destino;
    }

    @Override
    public String entidade() {
        return "produto";
    }

    @Override
    public String consulta() {
        return """
                SELECT id, nome, descricao, categoria_id, fornecedor_padrao_id, preco_custo, preco_venda,
                       estoque_minimo, codigo_barras, ativo
                  FROM produto WHERE id > :ultimo ORDER BY id LIMIT :lote
                """;
    }

    @Override
    public Desfecho migrar(Linha l, Contexto ctx) {
        Optional<Long> categoria = ctx.idNovo("categoria_produto", l.longo("categoria_id"));
        if (categoria.isEmpty()) {
            return Desfecho.ignorado("CATEGORIA_NAO_MIGRADA");
        }
        BigDecimal venda = l.decimal("preco_venda");
        if (venda == null || venda.signum() <= 0) {
            return Desfecho.ignorado("PRECO_VENDA_INVALIDO");
        }
        List<String> pend = new ArrayList<>();
        Long fornecedor = null;
        if (l.longo("fornecedor_padrao_id") != null) {
            fornecedor = ctx.idNovo("fornecedor", l.longo("fornecedor_padrao_id")).orElse(null);
            if (fornecedor == null) {
                pend.add("FORNECEDOR_NAO_MIGRADO");
            }
        }
        String codigo = TextoNormalizado.limitar(l.texto("codigo_barras"), 50);
        if (codigo != null && destino.codigoBarrasExiste(codigo)) {
            pend.add("CODIGO_BARRAS_DUPLICADO");
            codigo = null;
        }
        BigDecimal custo = l.decimal("preco_custo");
        if (custo != null && custo.signum() <= 0) {
            pend.add("PRECO_CUSTO_INVALIDO");
            custo = null;
        }
        Integer minimo = l.inteiro("estoque_minimo");
        if (minimo != null && minimo < 0) {
            pend.add("ESTOQUE_MINIMO_INVALIDO");
            minimo = null;
        }
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("nome", TextoNormalizado.limitar(l.texto("nome"), 100));
        v.put("descricao", l.texto("descricao"));
        v.put("categoria_id", categoria.get());
        v.put("fornecedor_padrao_id", fornecedor);
        v.put("codigo_barras", codigo);
        v.put("preco_custo", custo);
        v.put("preco_venda", venda);
        v.put("quantidade_estoque", 0);
        v.put("estoque_minimo", minimo == null ? 5 : minimo);
        v.put("ativo", Campos.ativo(l, pend));
        return Desfecho.migrado(destino.inserir("produto", v), pend);
    }
}
