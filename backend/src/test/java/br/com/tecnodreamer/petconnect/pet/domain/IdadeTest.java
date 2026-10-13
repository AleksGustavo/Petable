package br.com.tecnodreamer.petconnect.pet.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class IdadeTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 9, 25);

    @Test
    void descreveSingularPluralEFilhote() {
        assertThat(Idade.calcular(LocalDate.of(2025, 8, 25), HOJE).descricao()).isEqualTo("1 ano e 1 mês");
        assertThat(Idade.calcular(LocalDate.of(2024, 9, 25), HOJE).descricao()).isEqualTo("2 anos");
        assertThat(Idade.calcular(LocalDate.of(2026, 6, 1), HOJE).descricao()).isEqualTo("3 meses");
        assertThat(Idade.calcular(LocalDate.of(2026, 9, 10), HOJE).descricao()).isEqualTo("menos de 1 mês");
    }
}
