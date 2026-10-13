package br.com.tecnodreamer.petconnect.pet.infrastructure;

import br.com.tecnodreamer.petconnect.pet.domain.Especie;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EspecieRepository extends JpaRepository<Especie, Long> {

    List<Especie> findAllByOrderByNomeAsc();
}
