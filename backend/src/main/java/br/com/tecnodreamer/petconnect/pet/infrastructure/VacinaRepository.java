package br.com.tecnodreamer.petconnect.pet.infrastructure;

import br.com.tecnodreamer.petconnect.pet.domain.Vacina;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VacinaRepository extends JpaRepository<Vacina, Long> {

    List<Vacina> findByEspecieIdAndAtivoTrueOrderByNomeAsc(Long especieId);
}
