package br.com.tecnodreamer.petconnect.shared.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);

    private final AuditoriaRepository repository;

    public AuditoriaService(AuditoriaRepository repository) {
        this.repository = repository;
    }

    /** Usa o usuário autenticado da requisição atual. */
    public void registrar(String acao, String entidade, Object entidadeId, Map<String, Object> detalhes) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        registrar(auth != null ? auth.getName() : "anonimo", acao, entidade, entidadeId, detalhes);
    }

    /** Auditoria nunca derruba a operação de negócio: falha vira log. */
    public void registrar(String usuario, String acao, String entidade, Object entidadeId,
            Map<String, Object> detalhes) {
        try {
            repository.save(new EventoAuditoria(null, Instant.now(), usuario, acao, entidade,
                    entidadeId == null ? null : String.valueOf(entidadeId),
                    detalhes == null ? Map.of() : detalhes));
        } catch (RuntimeException e) {
            log.error("Falha ao gravar auditoria {} {} {}", acao, entidade, entidadeId, e);
        }
    }
}
