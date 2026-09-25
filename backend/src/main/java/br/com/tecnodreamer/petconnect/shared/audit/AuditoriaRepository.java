package br.com.tecnodreamer.petconnect.shared.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuditoriaRepository extends MongoRepository<EventoAuditoria, String> {

    Page<EventoAuditoria> findByEntidadeAndEntidadeId(String entidade, String entidadeId, Pageable pageable);
}
