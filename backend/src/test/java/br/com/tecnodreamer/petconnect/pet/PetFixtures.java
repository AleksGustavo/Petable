package br.com.tecnodreamer.petconnect.pet;

import br.com.tecnodreamer.petconnect.cliente.domain.Cliente;
import br.com.tecnodreamer.petconnect.pet.domain.DadosPet;
import br.com.tecnodreamer.petconnect.pet.domain.Especie;
import br.com.tecnodreamer.petconnect.pet.domain.Pet;
import br.com.tecnodreamer.petconnect.pet.domain.Raca;
import br.com.tecnodreamer.petconnect.pet.domain.Vacina;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Entidades montadas em memória para testes unitários (ids normalmente vêm do banco). */
public final class PetFixtures {

    /** "Hoje" dos testes: 25/09/2026 em Brasília. */
    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-25T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));
    public static final LocalDate HOJE = LocalDate.of(2026, 9, 25);

    private PetFixtures() {
    }

    private static <T> T novo(Class<T> tipo, Long id) {
        T obj = BeanUtils.instantiateClass(tipo);
        ReflectionTestUtils.setField(obj, "id", id);
        return obj;
    }

    public static Especie especie(Long id, String nome) {
        Especie e = novo(Especie.class, id);
        ReflectionTestUtils.setField(e, "nome", nome);
        return e;
    }

    public static Raca raca(Long id, String nome, Especie especie) {
        Raca r = novo(Raca.class, id);
        ReflectionTestUtils.setField(r, "nome", nome);
        ReflectionTestUtils.setField(r, "especie", especie);
        return r;
    }

    public static Vacina vacina(Long id, String nome, Especie especie, boolean ativo) {
        Vacina v = novo(Vacina.class, id);
        ReflectionTestUtils.setField(v, "nome", nome);
        ReflectionTestUtils.setField(v, "especie", especie);
        ReflectionTestUtils.setField(v, "ativo", ativo);
        return v;
    }

    public static Cliente cliente(Long id) {
        Cliente c = new Cliente("Tutor " + id, null, null, "1999900000" + id, null, null);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    public static Pet pet(Long id, Cliente tutor, Especie especie, Raca raca, LocalDate nascimento) {
        Pet p = new Pet(new DadosPet(tutor, "Mel", especie, raca, nascimento, null, null, null, false, null));
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }
}
