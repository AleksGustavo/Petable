package br.com.tecnodreamer.petconnect.usuario.infrastructure;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Janela deslizante: até 3 pedidos por e-mail e 10 por IP a cada 30 minutos (DA-10). */
@Component
public class LimitadorRedefinicaoEmMemoria implements LimitadorRedefinicao {

    static final int MAX_POR_EMAIL = 3;
    static final int MAX_POR_IP = 10;
    static final Duration JANELA = Duration.ofMinutes(30);

    private final Map<String, Deque<Instant>> tentativas = new ConcurrentHashMap<>();
    private final Clock clock;

    public LimitadorRedefinicaoEmMemoria(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean permitir(String email, String ip) {
        Instant agora = clock.instant();
        boolean porEmail = registrar("email:" + (email == null ? "" : email.trim().toLowerCase(Locale.ROOT)), MAX_POR_EMAIL, agora);
        boolean porIp = registrar("ip:" + (ip == null ? "desconhecido" : ip), MAX_POR_IP, agora);
        return porEmail && porIp;
    }

    private boolean registrar(String chave, int maximo, Instant agora) {
        Deque<Instant> fila = tentativas.computeIfAbsent(chave, k -> new ArrayDeque<>());
        synchronized (fila) {
            while (!fila.isEmpty() && !fila.peekFirst().plus(JANELA).isAfter(agora)) {
                fila.pollFirst();
            }
            fila.addLast(agora);
            return fila.size() <= maximo;
        }
    }
}
