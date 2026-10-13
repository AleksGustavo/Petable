package br.com.tecnodreamer.petconnect.agendamento.infrastructure;

import br.com.tecnodreamer.petconnect.agendamento.domain.Agendamento;
import br.com.tecnodreamer.petconnect.agendamento.domain.StatusAgendamento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AgendamentoRepository extends JpaRepository<Agendamento, Long> {

    @EntityGraph(attributePaths = {"pet", "pet.cliente", "servico", "funcionario", "funcionario.usuario"})
    @Query("""
            select a from Agendamento a join a.pet p join p.cliente c
            where a.dataHora >= :de and a.dataHora < :ate
              and (:status is null or a.status = :status)
              and (:petId is null or p.id = :petId)
              and (:clienteId is null or c.id = :clienteId)
              and (:servicoId is null or a.servico.id = :servicoId)
              and (:termo is null
                   or lower(p.nome) like lower(concat('%', :termo, '%'))
                   or lower(c.nome) like lower(concat('%', :termo, '%')))
            """)
    Page<Agendamento> buscar(LocalDateTime de, LocalDateTime ate, StatusAgendamento status, Long petId,
            Long clienteId, Long servicoId, String termo, Pageable pageable);

    @EntityGraph(attributePaths = {"pet", "pet.cliente", "pet.especie", "servico", "funcionario", "funcionario.usuario"})
    Optional<Agendamento> findWithDetalhesById(Long id);

    /**
     * Candidatos a conflito do pet: ativos que começam antes do fim do novo e
     * depois de {@code desde} (início do novo menos a maior duração possível).
     * A sobreposição exata é conferida em Java com a duração de cada serviço.
     */
    @EntityGraph(attributePaths = "servico")
    @Query("""
            select a from Agendamento a
            where a.pet.id = :petId and a.status in :status and a.dataHora >= :desde and a.dataHora < :ate
            """)
    List<Agendamento> candidatosPorPet(Long petId, Collection<StatusAgendamento> status, LocalDateTime desde,
            LocalDateTime ate);

    @EntityGraph(attributePaths = "servico")
    @Query("""
            select a from Agendamento a
            where a.funcionario.id = :funcionarioId and a.status in :status
              and a.dataHora >= :desde and a.dataHora < :ate
            """)
    List<Agendamento> candidatosPorFuncionario(Long funcionarioId, Collection<StatusAgendamento> status,
            LocalDateTime desde, LocalDateTime ate);

    /** Próximos do pet: futuros agendados/confirmados e qualquer um em andamento (mesmo que já tenha começado). */
    @EntityGraph(attributePaths = {"pet", "pet.cliente", "servico", "funcionario", "funcionario.usuario"})
    @Query("""
            select a from Agendamento a
            where a.pet.id = :petId
              and (a.status = :emAndamento or (a.status in :futuros and a.dataHora >= :agora))
            order by a.dataHora asc, a.id asc
            """)
    List<Agendamento> proximos(Long petId, StatusAgendamento emAndamento, Collection<StatusAgendamento> futuros,
            LocalDateTime agora, Pageable limite);
}
