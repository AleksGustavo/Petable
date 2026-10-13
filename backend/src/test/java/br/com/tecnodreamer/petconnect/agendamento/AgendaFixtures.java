package br.com.tecnodreamer.petconnect.agendamento;

import br.com.tecnodreamer.petconnect.agendamento.domain.Agendamento;
import br.com.tecnodreamer.petconnect.agendamento.domain.StatusAgendamento;
import br.com.tecnodreamer.petconnect.cliente.domain.Cliente;
import br.com.tecnodreamer.petconnect.funcionario.domain.Funcionario;
import br.com.tecnodreamer.petconnect.usuario.domain.Usuario;
import br.com.tecnodreamer.petconnect.pet.domain.DadosPet;
import br.com.tecnodreamer.petconnect.pet.domain.Especie;
import br.com.tecnodreamer.petconnect.pet.domain.Pet;
import br.com.tecnodreamer.petconnect.pet.domain.Porte;
import br.com.tecnodreamer.petconnect.servico.domain.Servico;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

import static br.com.tecnodreamer.petconnect.pet.PetFixtures.especie;

/** Entidades em memória para os testes unitários de agendamento. "Hoje" = PetFixtures.CLOCK (25/09/2026 12:00). */
public final class AgendaFixtures {

    public static final LocalDateTime AGORA = LocalDateTime.of(2026, 9, 25, 12, 0);
    public static final LocalDateTime SEGUNDA_9H = LocalDateTime.of(2026, 9, 28, 9, 0);
    public static final Especie CAO = especie(1L, "Cão");

    private AgendaFixtures() {
    }

    public static Servico servico(Long id, String nome, String preco, int duracao, Map<Porte, BigDecimal> porPorte) {
        Map<Porte, BigDecimal> precos = new EnumMap<>(Porte.class);
        precos.putAll(porPorte);
        Servico s = new Servico(nome, null, new BigDecimal(preco), duracao, precos);
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }

    /** Banho: padrão 50,00 / 60 min; MÉDIO 55,00 e GRANDE 70,00 (sem preço para PEQUENO). */
    public static Servico banho() {
        return servico(1L, "Banho", "50.00", 60, Map.of(Porte.MEDIO, new BigDecimal("55.00"), Porte.GRANDE, new BigDecimal("70.00")));
    }

    public static Servico vacinacao() {
        return servico(9L, "Vacinação", "80.00", 15, Map.of());
    }

    public static Pet pet(Long id, Porte porte) {
        Cliente tutor = new Cliente("Tutor", null, null, "19999990000", null, null);
        ReflectionTestUtils.setField(tutor, "id", 7L);
        Pet p = new Pet(new DadosPet(tutor, "Mel", CAO, null, null, null, null, porte, false, null));
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    public static Funcionario funcionario(Long id) {
        Funcionario f = BeanUtils.instantiateClass(Funcionario.class);
        ReflectionTestUtils.setField(f, "id", id);
        ReflectionTestUtils.setField(f, "nome", "Func " + id);
        Usuario u = new Usuario("func" + id, "func" + id + "@petconnect.test", "h", null);
        ReflectionTestUtils.setField(f, "usuario", u);
        return f;
    }

    public static Agendamento agendamento(Long id, Pet pet, Servico servico, LocalDateTime dataHora, StatusAgendamento status) {
        Agendamento a = new Agendamento(pet, servico, null, dataHora, servico.estimarPara(pet.getPorte()).valor(), null);
        ReflectionTestUtils.setField(a, "id", id);
        ReflectionTestUtils.setField(a, "status", status);
        return a;
    }
}
