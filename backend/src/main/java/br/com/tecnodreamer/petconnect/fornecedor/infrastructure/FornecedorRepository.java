package br.com.tecnodreamer.petconnect.fornecedor.infrastructure;

import br.com.tecnodreamer.petconnect.fornecedor.domain.Fornecedor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FornecedorRepository extends JpaRepository<Fornecedor, Long> {

    /** Busca por nome fantasia, razão social, contato (contém) ou CNPJ (prefixo, só dígitos). */
    @Query("""
            select f from Fornecedor f
            where (:ativo is null or f.ativo = :ativo)
              and (:termo is null
                   or lower(f.nomeFantasia) like lower(concat('%', :termo, '%'))
                   or lower(f.razaoSocial) like lower(concat('%', :termo, '%'))
                   or lower(f.contato) like lower(concat('%', :termo, '%'))
                   or (:digitos is not null and f.cnpj like concat(:digitos, '%')))
            """)
    Page<Fornecedor> buscar(String termo, String digitos, Boolean ativo, Pageable pageable);

    @Query("""
            select f from Fornecedor f
            where f.ativo = true and (:termo is null or lower(f.nomeFantasia) like lower(concat('%', :termo, '%'))
                                       or f.cnpj like concat(:termo, '%'))
            order by f.nomeFantasia, f.id
            """)
    List<Fornecedor> opcoes(String termo, Pageable limite);

    @EntityGraph(attributePaths = "endereco")
    Optional<Fornecedor> findWithEnderecoById(Long id);

    boolean existsByCnpj(String cnpj);

    boolean existsByCnpjAndIdNot(String cnpj, Long id);

    boolean existsByTelefone(String telefone);

    boolean existsByTelefoneAndIdNot(String telefone, Long id);
}
