package br.com.tecnodreamer.petconnect.shared.error;

/**
 * Regra de negócio violada em um campo específico do request (ex.: tutor
 * inexistente, data futura). Vira 400 com {@code errors: {campo: mensagem}},
 * no mesmo formato da Bean Validation, para o front marcar o campo.
 */
public class CampoInvalidoException extends RuntimeException {

    private final String campo;

    public CampoInvalidoException(String campo, String mensagem) {
        super(mensagem);
        this.campo = campo;
    }

    public String getCampo() {
        return campo;
    }
}
