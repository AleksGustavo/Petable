package br.com.tecnodreamer.petconnect.agendamento.infrastructure;

import br.com.tecnodreamer.petconnect.agendamento.domain.ServicoRealizado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ServicoRealizadoRepository extends JpaRepository<ServicoRealizado, Long> {

    boolean existsByAgendamentoId(Long agendamentoId);

    Optional<ServicoRealizado> findByAgendamentoId(Long agendamentoId);
}
