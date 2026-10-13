package br.com.tecnodreamer.petconnect.shared.error;

import java.util.Map;

public class ConflictException extends RuntimeException {

    private final Map<String, Object> propriedades;

    public ConflictException(String message) {
        this(message, Map.of());
    }

    /** Propriedades extras que vão para o ProblemDetail (ex.: produto sem estoque). */
    protected ConflictException(String message, Map<String, Object> propriedades) {
        super(message);
        this.propriedades = Map.copyOf(propriedades);
    }

    public Map<String, Object> getPropriedades() {
        return propriedades;
    }
}
