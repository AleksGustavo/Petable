package br.com.tecnodreamer.petconnect.auth.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite de tentativas de login FALHAS por IP (DA-14): janela deslizante de 5 minutos.
 * Em memória porque a produção roda uma instância (DA-01); soma-se ao bloqueio por conta.
 */
@Component
public class LimitadorLogin {

    static final Duration JANELA = Duration.ofMinutes(5);

    private final Map<String, Deque<Instant>> falhas = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maximo;

    public LimitadorLogin(Clock clock, @Value("${app.security.login-ip.maximo:20}") int maximo) {
        this.clock = clock;
        this.maximo = maximo;
    }

    /** Vazio = pode tentar; senão, segundos até liberar (Retry-After). */
    public Optional<Long> bloqueio(String ip) {
        Deque<Instant> fila = falhas.get(chave(ip));
        if (fila == null) {
            return Optional.empty();
        }
        Instant agora = clock.instant();
        synchronized (fila) {
            limpar(fila, agora);
            if (fila.size() < maximo) {
                return Optional.empty();
            }
            long segundos = Duration.between(agora, fila.peekFirst().plus(JANELA)).toSeconds();
            return Optional.of(Math.max(1, segundos));
        }
    }

    public void falhou(String ip) {
        Deque<Instant> fila = falhas.computeIfAbsent(chave(ip), k -> new ArrayDeque<>());
        Instant agora = clock.instant();
        synchronized (fila) {
            limpar(fila, agora);
            fila.addLast(agora);
        }
    }

    private static void limpar(Deque<Instant> fila, Instant agora) {
        while (!fila.isEmpty() && !fila.peekFirst().plus(JANELA).isAfter(agora)) {
            fila.pollFirst();
        }
    }

    private static String chave(String ip) {
        return ip == null || ip.isBlank() ? "desconhecido" : ip;
    }
}
