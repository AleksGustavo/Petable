package br.com.tecnodreamer.petconnect.shared.error;

public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
