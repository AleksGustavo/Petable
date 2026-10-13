package br.com.tecnodreamer.petconnect.pet.infrastructure;

import br.com.tecnodreamer.petconnect.pet.domain.Pet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PetRepository extends JpaRepository<Pet, Long> {

    /**
     * Pets ativos de tutores ativos, com busca (contém, sem diferenciar
     * maiúsculas) no nome do pet ou do tutor e filtro opcional por tutor.
     */
    @EntityGraph(attributePaths = {"cliente", "especie", "raca"})
    @Query("""
            select p from Pet p join p.cliente c
            where p.ativo = true and c.ativo = true
              and (:clienteId is null or c.id = :clienteId)
              and (:termo is null
                   or lower(p.nome) like lower(concat('%', :termo, '%'))
                   or lower(c.nome) like lower(concat('%', :termo, '%')))
            """)
    Page<Pet> buscarAtivos(String termo, Long clienteId, Pageable pageable);

    @EntityGraph(attributePaths = {"cliente", "especie", "raca"})
    Optional<Pet> findWithDetalhesById(Long id);

    /**
     * Carrega o pet com lock de escrita (SELECT ... FOR UPDATE) até o fim da
     * transação: serializa criação/edição de agendamentos do mesmo pet para a
     * verificação de conflito não ser furada por requisições simultâneas.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pet p where p.id = :id")
    Optional<Pet> travarParaAgendamento(Long id);
}
