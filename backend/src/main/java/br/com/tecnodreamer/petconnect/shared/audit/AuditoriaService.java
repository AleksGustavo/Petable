package br.com.tecnodreamer.petconnect.shared.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.function.Supplier;

@Service
public class AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);

    /**
     * Migração do legado (etapa 08): milhares de gravações passam pelos serviços de domínio,
     * que auditam cada uma. Nesse escopo a auditoria por registro é suprimida e a migração
     * registra um único evento com contagens. O MongoDB não participa do rollback do dry-run.
     */
    private static final ThreadLocal<Boolean> SUPRIMIDA = ThreadLocal.withInitial(() -> false);

    private final AuditoriaRepository repository;

    public AuditoriaService(AuditoriaRepository repository) {
        this.repository = repository;
    }

    /** Usa o usuário autenticado da requisição atual. */
    public void registrar(String acao, String entidade, Object entidadeId, Map<String, Object> detalhes) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        registrar(auth != null ? auth.getName() : "anonimo", acao, entidade, entidadeId, detalhes);
    }

    /** Executa {@code acao} sem gravar os eventos de auditoria gerados dentro dela (mesma thread). */
    public <T> T semAuditoriaPorRegistro(Supplier<T> acao) {
        boolean anterior = SUPRIMIDA.get();
        SUPRIMIDA.set(true);
        try {
            return acao.get();
        } finally {
            SUPRIMIDA.set(anterior);
        }
    }

    /** Evento que precisa ser gravado mesmo dentro de {@link #semAuditoriaPorRegistro}. */
    public void registrarSempre(String usuario, String acao, String entidade, Object entidadeId,
            Map<String, Object> detalhes) {
        boolean anterior = SUPRIMIDA.get();
        SUPRIMIDA.set(false);
        try {
            registrar(usuario, acao, entidade, entidadeId, detalhes);
        } finally {
            SUPRIMIDA.set(anterior);
        }
    }

    /** Auditoria nunca derruba a operação de negócio: falha vira log. */
    public void registrar(String usuario, String acao, String entidade, Object entidadeId,
            Map<String, Object> detalhes) {
        if (SUPRIMIDA.get()) {
            return;
        }
        try {
            repository.save(new EventoAuditoria(null, Instant.now(), usuario, acao, entidade,
                    entidadeId == null ? null : String.valueOf(entidadeId),
                    detalhes == null ? Map.of() : detalhes));
        } catch (RuntimeException e) {
            log.error("Falha ao gravar auditoria {} {} {}", acao, entidade, entidadeId, e);
        }
    }
}
