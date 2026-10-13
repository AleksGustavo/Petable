package br.com.tecnodreamer.petconnect.pet.infrastructure;

import br.com.tecnodreamer.petconnect.pet.domain.Raca;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RacaRepository extends JpaRepository<Raca, Long> {

    List<Raca> findByEspecieIdOrderByNomeAsc(Long especieId);
}
