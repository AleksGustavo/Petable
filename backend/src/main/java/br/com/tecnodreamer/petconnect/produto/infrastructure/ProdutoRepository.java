package br.com.tecnodreamer.petconnect.produto.infrastructure;

import br.com.tecnodreamer.petconnect.produto.domain.Produto;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    @EntityGraph(attributePaths = {"categoria", "fornecedorPadrao"})
    @Query("""
            select p from Produto p
            where (:ativo is null or p.ativo = :ativo)
              and (:categoriaId is null or p.categoria.id = :categoriaId)
              and (:abaixoMinimo = false or p.quantidadeEstoque <= p.estoqueMinimo)
              and (:termo is null
                   or lower(p.nome) like lower(concat('%', :termo, '%'))
                   or p.codigoBarras like concat(:termo, '%'))
            """)
    Page<Produto> buscar(String termo, Long categoriaId, Boolean ativo, boolean abaixoMinimo, Pageable pageable);

    /** Busca rápida (PDV): só ativos; código exato vem primeiro. */
    @Query("""
            select p from Produto p
            where p.ativo = true
              and (lower(p.nome) like lower(concat('%', :termo, '%')) or p.codigoBarras like concat(:termo, '%'))
            order by case when p.codigoBarras = :termo then 0 else 1 end, p.nome, p.id
            """)
    List<Produto> buscaRapida(String termo, Pageable limite);

    @EntityGraph(attributePaths = {"categoria", "fornecedorPadrao"})
    Optional<Produto> findWithDetalhesById(Long id);

    /** Trava o produto (SELECT ... FOR UPDATE) antes de mexer no saldo (DA-08). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Produto p where p.id = :id")
    Optional<Produto> travar(Long id);

    /** Trava vários produtos SEMPRE em ordem crescente de id: evita deadlock entre compras (R2). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Produto p where p.id in :ids order by p.id")
    List<Produto> travarEmOrdem(Collection<Long> ids);

    @Query("select count(p) from Produto p where p.ativo = true and p.quantidadeEstoque <= p.estoqueMinimo")
    long contarAbaixoDoMinimo();

    /** Produtos ativos com saldo que ainda não controlam lote (candidatos à regularização; só informativo). */
    @Query("select count(p) from Produto p where p.ativo = true and p.controlaLote = false and p.quantidadeEstoque > 0")
    long contarComSaldoSemControleDeLote();

    boolean existsByCodigoBarras(String codigoBarras);

    boolean existsByCodigoBarrasAndIdNot(String codigoBarras, Long id);
}
