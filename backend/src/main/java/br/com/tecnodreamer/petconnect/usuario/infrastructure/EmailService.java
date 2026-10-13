package br.com.tecnodreamer.petconnect.usuario.infrastructure;

/** Envio de e-mail transacional. Em dev/Docker vai para o Mailpit; em produção, SMTP real por variáveis de ambiente. */
public interface EmailService {

    void enviar(String para, String assunto, String texto);
}
