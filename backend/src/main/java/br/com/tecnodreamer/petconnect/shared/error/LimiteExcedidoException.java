package br.com.tecnodreamer.petconnect.shared.error;

/** 429 com Retry-After (segundos). */
public class LimiteExcedidoException extends RuntimeException {

    private final long retryAfter;

    public LimiteExcedidoException(String mensagem, long retryAfter) {
        super(mensagem);
        this.retryAfter = retryAfter;
    }

    public long getRetryAfter() {
        return retryAfter;
    }
}
