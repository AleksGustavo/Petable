package br.com.tecnodreamer.petconnect.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ClockConfig {

    /** Fuso do petshop: "hoje" (idade, vencimento de vacina, data futura) é o dia em Brasília, não em UTC. */
    @Bean
    Clock clock() {
        return Clock.system(ZoneId.of("America/Sao_Paulo"));
    }
}
