package br.com.tecnodreamer.petconnect.shared.audit;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * Trilha de auditoria — fica no MongoDB (e não no MySQL) porque é append-only,
 * de esquema flexível ({@code detalhes} muda por ação) e cresce muito mais que
 * os dados transacionais.
 */
@Document("auditoria")
public record EventoAuditoria(
        @Id String id,
        @Indexed Instant ocorridoEm,
        @Indexed String usuario,
        String acao,
        @Indexed String entidade,
        String entidadeId,
        Map<String, Object> detalhes) {
}
