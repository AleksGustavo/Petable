package br.com.tecnodreamer.petconnect;

import br.com.tecnodreamer.petconnect.usuario.infrastructure.EmailService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Substitui o SMTP nos ITs: guarda os e-mails para o teste ler o link de redefinição. */
@TestConfiguration(proxyBeanMethods = false)
public class EmailsCapturados {

    public record Email(String para, String assunto, String texto) {
    }

    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");
    private final List<Email> enviados = new CopyOnWriteArrayList<>();

    @Bean
    @Primary
    EmailService emailServiceDeTeste() {
        return (para, assunto, texto) -> enviados.add(new Email(para, assunto, texto));
    }

    public List<Email> para(String email) {
        return enviados.stream().filter(e -> e.para().equalsIgnoreCase(email)).toList();
    }

    /** Token do último e-mail de redefinição enviado ao endereço. */
    public Optional<String> ultimoToken(String email) {
        List<Email> lista = para(email);
        if (lista.isEmpty()) {
            return Optional.empty();
        }
        Matcher m = TOKEN.matcher(lista.getLast().texto());
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }
}
