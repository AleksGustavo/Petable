package br.com.tecnodreamer.petconnect.financeiro.infrastructure;

import br.com.tecnodreamer.petconnect.financeiro.domain.CategoriaLancamento;
import br.com.tecnodreamer.petconnect.financeiro.domain.Lancamento;
import br.com.tecnodreamer.petconnect.financeiro.domain.TipoLancamento;
import br.com.tecnodreamer.petconnect.venda.domain.FormaPagamento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LancamentoRepository extends JpaRepository<Lancamento, Long> {

    Optional<Lancamento> findByChaveOrigem(String chaveOrigem);

    Optional<Lancamento> findByEstornoDeId(Long estornoDeId);

    boolean existsByEstornoDeId(Long estornoDeId);

    @Query("""
            select l from Lancamento l
            where (:inicio is null or l.dataMovimentacao >= :inicio)
              and (:fim is null or l.dataMovimentacao <= :fim)
              and (:tipo is null or l.tipo = :tipo)
              and (:categoria is null or l.categoria = :categoria)
              and (:forma is null or l.formaPagamento = :forma)
              and (:prefixo is null or l.chaveOrigem like :prefixo)
            """)
    Page<Lancamento> buscar(LocalDate inicio, LocalDate fim, TipoLancamento tipo, CategoriaLancamento categoria,
            FormaPagamento forma, String prefixo, Pageable pageable);

    /** Saldo por forma dos lançamentos de um caixa (ENTRADA − SAIDA), agregado no banco. */
    @Query(value = """
            select forma_pagamento, coalesce(sum(case when tipo = 'ENTRADA' then valor else -valor end), 0)
            from movimentacao_financeira where caixa_id = :caixaId group by forma_pagamento
            """, nativeQuery = true)
    List<Object[]> saldoPorFormaDoCaixa(Long caixaId);

    @Query(value = """
            select categoria,
                   coalesce(sum(case when tipo = 'ENTRADA' then valor else 0 end), 0),
                   coalesce(sum(case when tipo = 'SAIDA' then valor else 0 end), 0)
            from movimentacao_financeira where data_movimentacao between :inicio and :fim group by categoria
            """, nativeQuery = true)
    List<Object[]> fluxoPorCategoria(LocalDate inicio, LocalDate fim);

    @Query(value = """
            select forma_pagamento,
                   coalesce(sum(case when tipo = 'ENTRADA' then valor else 0 end), 0),
                   coalesce(sum(case when tipo = 'SAIDA' then valor else 0 end), 0)
            from movimentacao_financeira where data_movimentacao between :inicio and :fim group by forma_pagamento
            """, nativeQuery = true)
    List<Object[]> fluxoPorForma(LocalDate inicio, LocalDate fim);

    // ------------------------------------------------------------ backfill (DA-18)

    @Query(value = """
            select v.id from venda v where not exists
              (select 1 from movimentacao_financeira m where m.chave_origem = concat('VENDA:', v.id))
            order by v.id
            """, nativeQuery = true)
    List<Long> vendasSemLancamento();

    @Query(value = """
            select v.id from venda v where v.status = 'CANCELADA' and not exists
              (select 1 from movimentacao_financeira m where m.chave_origem = concat('VENDA_ESTORNO:', v.id))
            order by v.id
            """, nativeQuery = true)
    List<Long> vendasCanceladasSemEstorno();

    @Query(value = """
            select s.id from servico_realizado s where s.valor > 0 and not exists
              (select 1 from movimentacao_financeira m where m.chave_origem = concat('SERVICO:', s.id))
            order by s.id
            """, nativeQuery = true)
    List<Long> servicosSemLancamento();
}
