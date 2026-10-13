package br.com.tecnodreamer.petconnect.produto.infrastructure;

import br.com.tecnodreamer.petconnect.produto.domain.MovimentacaoEstoque;
import br.com.tecnodreamer.petconnect.produto.domain.TipoMovimentacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MovimentacaoEstoqueRepository extends JpaRepository<MovimentacaoEstoque, Long> {

    @EntityGraph(attributePaths = {"usuario", "compra"})
    @Query("""
            select m from MovimentacaoEstoque m
            where m.produto.id = :produtoId and (:tipo is null or m.tipo = :tipo)
            order by m.createdAt desc, m.id desc
            """)
    Page<MovimentacaoEstoque> historico(Long produtoId, TipoMovimentacao tipo, Pageable pageable);

    List<MovimentacaoEstoque> findByCompraIdOrderByIdAsc(Long compraId);
}
