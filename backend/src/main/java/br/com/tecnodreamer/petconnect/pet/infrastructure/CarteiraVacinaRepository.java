package br.com.tecnodreamer.petconnect.pet.infrastructure;

import br.com.tecnodreamer.petconnect.pet.domain.CarteiraVacina;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CarteiraVacinaRepository extends JpaRepository<CarteiraVacina, Long> {

    @EntityGraph(attributePaths = "vacina")
    List<CarteiraVacina> findByPetIdOrderByDataAplicacaoDescIdDesc(Long petId);
}
