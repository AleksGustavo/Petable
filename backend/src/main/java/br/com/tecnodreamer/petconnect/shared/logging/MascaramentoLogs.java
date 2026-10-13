package br.com.tecnodreamer.petconnect.shared.logging;

import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/**
 * Plugado em logging.structured.json.customizer (perfil prod): aplica a máscara a todo
 * valor texto do JSON de log (mensagem, stack trace, MDC).
 */
public class MascaramentoLogs implements StructuredLoggingJsonMembersCustomizer<Object> {

    @Override
    public void customize(JsonWriter.Members<Object> members) {
        members.applyingValueProcessor(JsonWriter.ValueProcessor.of(String.class, MascaraDadosPessoais::mascarar));
    }
}
