package br.com.tecnodreamer.petconnect.financeiro.infrastructure;

import br.com.tecnodreamer.petconnect.financeiro.domain.CaixaMovimento;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CaixaMovimentoRepository extends JpaRepository<CaixaMovimento, Long> {

    @EntityGraph(attributePaths = "usuario")
    List<CaixaMovimento> findByCaixaIdOrderByIdAsc(Long caixaId);
}
