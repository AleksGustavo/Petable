package br.com.tecnodreamer.petconnect.cliente.infrastructure;

import br.com.tecnodreamer.petconnect.cliente.domain.Cliente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    /**
     * Busca rápida por nome (contém), CPF (prefixo) ou telefone (contém).
     * {@code digitos} é o termo só com números, ou {@code null} se não houver.
     */
    @EntityGraph(attributePaths = "endereco")
    @Query("""
            select c from Cliente c
            where c.ativo = true
              and (:termo is null
                   or lower(c.nome) like lower(concat('%', :termo, '%'))
                   or (:digitos is not null and (c.cpf like concat(:digitos, '%')
                                                 or c.telefone like concat('%', :digitos, '%'))))
            """)
    Page<Cliente> buscarAtivos(String termo, String digitos, Pageable pageable);

    @EntityGraph(attributePaths = "endereco")
    Optional<Cliente> findWithEnderecoById(Long id);

    boolean existsByCpf(String cpf);

    boolean existsByCpfAndIdNot(String cpf, Long id);

    boolean existsByTelefone(String telefone);

    boolean existsByTelefoneAndIdNot(String telefone, Long id);
}
