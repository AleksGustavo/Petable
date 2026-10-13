package br.com.tecnodreamer.petconnect.pet.infrastructure;

import br.com.tecnodreamer.petconnect.pet.domain.HistoricoConsulta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistoricoConsultaRepository extends JpaRepository<HistoricoConsulta, Long> {

    List<HistoricoConsulta> findByPetIdOrderByDataConsultaDescIdDesc(Long petId);
}
