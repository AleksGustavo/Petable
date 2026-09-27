package br.com.tecnodreamer.petconnect.pet.application;

import br.com.tecnodreamer.petconnect.pet.domain.HistoricoConsulta;
import br.com.tecnodreamer.petconnect.pet.domain.Pet;
import br.com.tecnodreamer.petconnect.pet.infrastructure.HistoricoConsultaRepository;
import br.com.tecnodreamer.petconnect.pet.infrastructure.PetRepository;
import br.com.tecnodreamer.petconnect.pet.web.ConsultaRequest;
import br.com.tecnodreamer.petconnect.pet.web.ConsultaResponse;
import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;
import br.com.tecnodreamer.petconnect.shared.error.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static br.com.tecnodreamer.petconnect.pet.PetFixtures.CLOCK;
import static br.com.tecnodreamer.petconnect.pet.PetFixtures.cliente;
import static br.com.tecnodreamer.petconnect.pet.PetFixtures.especie;
import static br.com.tecnodreamer.petconnect.pet.PetFixtures.pet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HistoricoConsultaServiceTest {

    @Mock PetRepository pets;
    @Mock HistoricoConsultaRepository consultas;
    @Mock AuditoriaService auditoria;

    HistoricoConsultaService service;
    final Pet rex = pet(1L, cliente(7L), especie(1L, "Cão"), null, null);
    final LocalDateTime agora = LocalDateTime.now(CLOCK);

    @BeforeEach
    void setUp() {
        service = new HistoricoConsultaService(pets, consultas, auditoria, CLOCK);
        when(pets.findById(1L)).thenReturn(Optional.of(rex));
        when(consultas.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static ConsultaRequest req(LocalDateTime data) {
        return new ConsultaRequest(data, " Dr. Paulo ", "Coceira", "Dermatite", "Banho medicamentoso", null);
    }

    @Test
    void deveRegistrarConsulta() {
        ConsultaResponse r = service.registrar(1L, req(agora.minusHours(1)));

        assertThat(r.veterinario()).isEqualTo("Dr. Paulo");
        assertThat(r.diagnostico()).isEqualTo("Dermatite");
        verify(auditoria).registrar(eq("CONSULTA_PET_REGISTRADA"), eq("pet"), eq(1L), anyMap());
    }

    @Test
    void deveFalharComPetInexistente() {
        when(pets.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrar(2L, req(agora))).isInstanceOf(NotFoundException.class);
    }

    @Test
    void deveFalharComPetInativo() {
        rex.desativar();

        assertThatThrownBy(() -> service.registrar(1L, req(agora))).isInstanceOf(NotFoundException.class);
        verify(consultas, never()).save(any());
    }

    @Test
    void deveFalharComDataConsultaFutura() {
        assertThatThrownBy(() -> service.registrar(1L, req(agora.plusMinutes(5))))
                .isInstanceOf(CampoInvalidoException.class)
                .extracting("campo").isEqualTo("dataConsulta");
    }

    @Test
    void deveListarConsultasDaMaisRecenteParaMaisAntiga() {
        var recente = new HistoricoConsulta(rex, agora.minusDays(1), "Dra. A", null, null, null, null);
        var antiga = new HistoricoConsulta(rex, agora.minusYears(1), "Dr. B", null, null, null, null);
        when(consultas.findByPetIdOrderByDataConsultaDescIdDesc(1L)).thenReturn(List.of(recente, antiga));

        List<ConsultaResponse> lista = service.listar(1L);

        assertThat(lista).extracting(ConsultaResponse::veterinario).containsExactly("Dra. A", "Dr. B");
        assertThat(lista.getFirst().dataConsulta()).isAfter(lista.getLast().dataConsulta());
    }
}
