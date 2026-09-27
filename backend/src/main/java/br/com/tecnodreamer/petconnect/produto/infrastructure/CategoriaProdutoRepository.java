package br.com.tecnodreamer.petconnect.produto.infrastructure;

import br.com.tecnodreamer.petconnect.produto.domain.CategoriaProduto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoriaProdutoRepository extends JpaRepository<CategoriaProduto, Long> {

    List<CategoriaProduto> findAllByOrderByNomeAsc();
}
