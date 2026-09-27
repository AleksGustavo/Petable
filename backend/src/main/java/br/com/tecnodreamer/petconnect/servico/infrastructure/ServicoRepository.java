package br.com.tecnodreamer.petconnect.servico.infrastructure;

import br.com.tecnodreamer.petconnect.servico.domain.Servico;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServicoRepository extends JpaRepository<Servico, Long> {

    @EntityGraph(attributePaths = "precos")
    List<Servico> findAllByOrderByNomeAsc();

    @EntityGraph(attributePaths = "precos")
    List<Servico> findByAtivoOrderByNomeAsc(boolean ativo);

    boolean existsByNomeIgnoreCase(String nome);

    boolean existsByNomeIgnoreCaseAndIdNot(String nome, Long id);
}
