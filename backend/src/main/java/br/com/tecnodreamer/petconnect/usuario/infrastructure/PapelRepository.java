package br.com.tecnodreamer.petconnect.usuario.infrastructure;

import br.com.tecnodreamer.petconnect.usuario.domain.Papel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PapelRepository extends JpaRepository<Papel, Long> {

    Optional<Papel> findByNome(String nome);
}
