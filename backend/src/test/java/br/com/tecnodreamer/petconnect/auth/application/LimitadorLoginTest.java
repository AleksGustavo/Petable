package br.com.tecnodreamer.petconnect.auth.application;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class LimitadorLoginTest {

    AtomicReference<Instant> agora = new AtomicReference<>(Instant.parse("2026-09-27T12:00:00Z"));
    Clock relogio = new Clock() {
        @Override
        public java.time.ZoneId getZone() {
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
    LimitadorLogin limitador = new LimitadorLogin(relogio, 20);

    private void falhar(String ip, int vezes) {
        for (int i = 0; i < vezes; i++) {
            limitador.falhou(ip);
        }
    }

    @Test
    void devePermitirAteOLimite() {
        falhar("1.1.1.1", 19);
        assertThat(limitador.bloqueio("1.1.1.1")).isEmpty();
    }

    @Test
    void deveBloquearAoAtingirOLimiteComRetryAfter() {
        falhar("1.1.1.1", 20);
        assertThat(limitador.bloqueio("1.1.1.1")).hasValue(300L);
        agora.set(agora.get().plusSeconds(60));
        assertThat(limitador.bloqueio("1.1.1.1")).hasValue(240L);
    }

    @Test
    void deveLiberarDepoisDaJanela() {
        falhar("1.1.1.1", 20);
        agora.set(agora.get().plus(LimitadorLogin.JANELA));
        assertThat(limitador.bloqueio("1.1.1.1")).isEmpty();
    }

    @Test
    void ipsSaoIndependentes() {
        falhar("1.1.1.1", 20);
        assertThat(limitador.bloqueio("2.2.2.2")).isEmpty();
        assertThat(limitador.bloqueio(null)).isEmpty();
    }
}
