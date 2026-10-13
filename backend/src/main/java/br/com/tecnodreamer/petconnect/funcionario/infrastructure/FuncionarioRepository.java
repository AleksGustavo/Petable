package br.com.tecnodreamer.petconnect.funcionario.infrastructure;

import br.com.tecnodreamer.petconnect.funcionario.domain.Funcionario;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FuncionarioRepository extends JpaRepository<Funcionario, Long> {

    /** Disponíveis para agenda: conta ativa, busca por nome, em ordem alfabética. */
    @EntityGraph(attributePaths = "usuario")
    @Query("""
            select f from Funcionario f
            where f.usuario.ativo = true and (:termo is null or lower(f.nome) like lower(concat('%', :termo, '%')))
            order by f.nome, f.id
            """)
    List<Funcionario> disponiveis(String termo);

    @EntityGraph(attributePaths = {"usuario", "endereco"})
    Optional<Funcionario> findWithDetalhesById(Long id);

    @EntityGraph(attributePaths = {"usuario", "endereco"})
    Optional<Funcionario> findByUsuarioId(Long usuarioId);

    @EntityGraph(attributePaths = "usuario")
    List<Funcionario> findByUsuarioIdIn(Collection<Long> usuarioIds);

    boolean existsByUsuarioId(Long usuarioId);

    boolean existsByCpf(String cpf);

    boolean existsByCpfAndIdNot(String cpf, Long id);

    boolean existsByTelefone(String telefone);

    boolean existsByTelefoneAndIdNot(String telefone, Long id);
}
