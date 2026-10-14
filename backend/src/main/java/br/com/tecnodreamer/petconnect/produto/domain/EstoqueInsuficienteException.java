package br.com.tecnodreamer.petconnect.produto.domain;

import br.com.tecnodreamer.petconnect.shared.error.ConflictException;

import java.util.Map;

/** 409 com {@code produtoId}, {@code disponivel} e {@code solicitado} no ProblemDetail (DA-05). */
public class EstoqueInsuficienteException extends ConflictException {

    private final Long produtoId;

    public EstoqueInsuficienteException(Produto produto, int solicitado) {
        super("Estoque insuficiente para %s: disponível %d, solicitado %d.".formatted(produto.getNome(),
                produto.getQuantidadeEstoque(), solicitado),
                Map.of("produtoId", produto.getId(), "disponivel", produto.getQuantidadeEstoque(), "solicitado", solicitado));
        this.produtoId = produto.getId();
    }

    /** Produto com lote: {@code disponivel} é só o que o FEFO pode vender hoje (não vencido, liberado, fora da margem). */
    public EstoqueInsuficienteException(Produto produto, int solicitado, int disponivel) {
        super("Estoque insuficiente para %s: disponível %d, solicitado %d.".formatted(produto.getNome(), disponivel, solicitado),
                Map.of("produtoId", produto.getId(), "disponivel", disponivel, "solicitado", solicitado));
        this.produtoId = produto.getId();
    }

    public Long getProdutoId() {
        return produtoId;
    }
}
