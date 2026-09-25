package br.com.tecnodreamer.petconnect.shared.error;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String recurso, Object id) {
        super(recurso + " " + id + " não encontrado");
    }
}
