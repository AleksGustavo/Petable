package br.com.tecnodreamer.petconnect.produto.infrastructure;

import br.com.tecnodreamer.petconnect.produto.domain.LoteEstoque;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Leituras de lote. Quem escreve num lote já travou a linha do produto (D15): o lote não tem
 * trava própria, então estas consultas só fazem sentido dentro da transação do estoque.
 */
public interface LoteEstoqueRepository extends JpaRepository<LoteEstoque, Long> {

    Optional<LoteEstoque> findByProdutoIdAndCodigoLote(Long produtoId, String codigoLote);

    /**
     * Ordem FEFO (D6): validade crescente, lotes sem validade por último, desempate por id (que cresce
     * junto com created_at). Só DISPONIVEL com saldo e validade {@code >= limite}, onde
     * {@code limite = hoje + margem de bloqueio}. Atendido pelo índice {@code idx_lote_fefo}.
     */
    @Query("""
            select l from LoteEstoque l
            where l.produto.id = :produtoId and l.status = 'DISPONIVEL' and l.saldo > 0
              and (l.validade is null or l.validade >= :limite)
            order by case when l.validade is null then 1 else 0 end, l.validade, l.id
            """)
    List<LoteEstoque> vendaveisEmOrdemFefo(Long produtoId, LocalDate limite);

    List<LoteEstoque> findByProdutoIdOrderByIdAsc(Long produtoId);

    /** Soma dos saldos dos lotes do produto (I3). */
    @Query("select coalesce(sum(l.saldo), 0) from LoteEstoque l where l.produto.id = :produtoId")
    long somaSaldos(Long produtoId);

    /** I2: lotes cujo saldo difere da soma dos movimentos. Esperado: vazio. */
    @Query("""
            select l.id from LoteEstoque l
            where l.saldo <> coalesce((select sum(m.quantidade) from MovimentacaoEstoque m where m.lote.id = l.id), 0)
            order by l.id
            """)
    List<Long> divergenciasDeSaldoDoLote();

    /** I3: produto controlado cujo total difere da soma dos lotes. Esperado: vazio. */
    @Query("""
            select p.id from Produto p
            where p.controlaLote = true
              and p.quantidadeEstoque <> coalesce((select sum(l.saldo) from LoteEstoque l where l.produto.id = p.id), 0)
            order by p.id
            """)
    List<Long> divergenciasDoProdutoControlado();
}
