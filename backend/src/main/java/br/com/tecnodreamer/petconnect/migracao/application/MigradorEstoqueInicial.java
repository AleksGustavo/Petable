package br.com.tecnodreamer.petconnect.migracao.application;

import br.com.tecnodreamer.petconnect.produto.application.EstoqueService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Saldo inicial (DA-22, DA-23): um único AJUSTE por produto, pelo EstoqueService (único
 * que altera saldo), com o usuário técnico. Saldo legado negativo → 0 + pendência.
 * Vendas e compras históricas NÃO geram movimentos: este saldo já é o final.
 */
@Component
@Order(170)
public class MigradorEstoqueInicial implements Migrador {

    static final String MOTIVO = "Saldo inicial migrado do sistema antigo";

    private final EstoqueService estoque;

    public MigradorEstoqueInicial(EstoqueService estoque) {
        this.estoque = estoque;
    }

    @Override
    public String entidade() {
        return "produto_estoque";
    }

    @Override
    public String consulta() {
        return "SELECT id, quantidade_estoque FROM produto WHERE id > :ultimo ORDER BY id LIMIT :lote";
    }

    @Override
    public Desfecho migrar(Linha l, Contexto ctx) {
        Optional<Long> produto = ctx.idNovo("produto", l.id());
        if (produto.isEmpty()) {
            return Desfecho.ignorado("PRODUTO_NAO_MIGRADO");
        }
        Integer saldo = l.inteiro("quantidade_estoque");
        if (saldo != null && saldo < 0) {
            return Desfecho.migrado(produto.get(), List.of("SALDO_NEGATIVO"));
        }
        if (saldo != null && saldo > 0) {
            estoque.ajustar(produto.get(), saldo, MOTIVO, ctx.usuarioTecnicoId());
        }
        return Desfecho.migrado(produto.get());
    }
}
