package br.com.tecnodreamer.petconnect.auth.application;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptLimiterTest {

    private final AtomicReference<Instant> agora = new AtomicReference<>(Instant.parse("2026-01-01T10:00:00Z"));
    private final Clock clock = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora.get();
        }
    };
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock);

    @Test
    void bloqueiaAposMaximoDeFalhasEIgnoraMaiusculas() {
        for (int i = 0; i < LoginAttemptLimiter.MAX_FALHAS; i++) {
            assertThat(limiter.bloqueado("Admin")).isFalse();
            limiter.falhou("Admin");
        }
        assertThat(limiter.bloqueado("admin")).isTrue();
    }

    @Test
    void desbloqueiaDepoisDoPrazo() {
        for (int i = 0; i < LoginAttemptLimiter.MAX_FALHAS; i++) {
            limiter.falhou("ana");
        }
        agora.set(agora.get().plus(LoginAttemptLimiter.BLOQUEIO).plusSeconds(1));
        assertThat(limiter.bloqueado("ana")).isFalse();
    }

    @Test
    void sucessoZeraContagem() {
        for (int i = 0; i < LoginAttemptLimiter.MAX_FALHAS - 1; i++) {
            limiter.falhou("ana");
        }
        limiter.sucesso("ana");
        limiter.falhou("ana");
        assertThat(limiter.bloqueado("ana")).isFalse();
    }
}
