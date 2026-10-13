package br.com.tecnodreamer.petconnect.auth.application;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bloqueia um login após {@value #MAX_FALHAS} falhas seguidas por
 * {@link #BLOQUEIO}. Em memória: suficiente para uma instância; com várias
 * réplicas, trocar por Redis/Bucket4j.
 */
@Component
public class LoginAttemptLimiter {

    static final int MAX_FALHAS = 5;
    static final Duration BLOQUEIO = Duration.ofMinutes(15);

    private record Tentativas(int falhas, Instant ultimaFalha) {
    }

    private final Map<String, Tentativas> porLogin = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    public boolean bloqueado(String login) {
        Tentativas t = porLogin.get(chave(login));
        if (t == null || t.falhas() < MAX_FALHAS) {
            return false;
        }
        if (t.ultimaFalha().plus(BLOQUEIO).isBefore(clock.instant())) {
            porLogin.remove(chave(login));
            return false;
        }
        return true;
    }

    public void falhou(String login) {
        porLogin.merge(chave(login), new Tentativas(1, clock.instant()),
                (atual, nova) -> new Tentativas(atual.falhas() + 1, nova.ultimaFalha()));
    }

    public void sucesso(String login) {
        porLogin.remove(chave(login));
    }

    private static String chave(String login) {
        return login.trim().toLowerCase(Locale.ROOT);
    }
}
