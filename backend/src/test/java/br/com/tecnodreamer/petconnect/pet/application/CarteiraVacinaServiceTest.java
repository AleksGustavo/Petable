package br.com.tecnodreamer.petconnect.pet.application;

import br.com.tecnodreamer.petconnect.pet.domain.CarteiraVacina;
import br.com.tecnodreamer.petconnect.pet.domain.Especie;
import br.com.tecnodreamer.petconnect.pet.domain.Pet;
import br.com.tecnodreamer.petconnect.pet.domain.StatusVacina;
import br.com.tecnodreamer.petconnect.pet.domain.Vacina;
import br.com.tecnodreamer.petconnect.pet.infrastructure.CarteiraVacinaRepository;
import br.com.tecnodreamer.petconnect.pet.infrastructure.PetRepository;
import br.com.tecnodreamer.petconnect.pet.infrastructure.VacinaRepository;
import br.com.tecnodreamer.petconnect.pet.web.LancamentoVacinaResponse;
import br.com.tecnodreamer.petconnect.pet.web.VacinaLancamentoRequest;
import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;
import br.com.tecnodreamer.petconnect.shared.error.ConflictException;
import br.com.tecnodreamer.petconnect.shared.error.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static br.com.tecnodreamer.petconnect.pet.PetFixtures.CLOCK;
import static br.com.tecnodreamer.petconnect.pet.PetFixtures.HOJE;
import static br.com.tecnodreamer.petconnect.pet.PetFixtures.cliente;
import static br.com.tecnodreamer.petconnect.pet.PetFixtures.especie;
import static br.com.tecnodreamer.petconnect.pet.PetFixtures.pet;
import static br.com.tecnodreamer.petconnect.pet.PetFixtures.vacina;
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
class CarteiraVacinaServiceTest {

    @Mock PetRepository pets;
    @Mock CarteiraVacinaRepository carteira;
    @Mock VacinaRepository vacinas;
    @Mock AuditoriaService auditoria;

    CarteiraVacinaService service;

    final Especie cao = especie(1L, "Cão");
    final Especie gato = especie(2L, "Gato");
    final Pet rex = pet(1L, cliente(7L), cao, null, null);
    final Vacina raiva = vacina(4L, "Raiva (Antirrábica Canina)", cao, true);

    @BeforeEach
    void setUp() {
        service = new CarteiraVacinaService(pets, carteira, vacinas, auditoria, CLOCK);
        when(pets.findById(1L)).thenReturn(Optional.of(rex));
        when(vacinas.findById(4L)).thenReturn(Optional.of(raiva));
        when(carteira.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static VacinaLancamentoRequest req(Long vacinaId, LocalDate aplicacao, LocalDate proxima) {
        return new VacinaLancamentoRequest(vacinaId, aplicacao, proxima, " Dra. Ana ", "L-1", null);
    }

    private StatusVacina statusCom(LocalDate proxima) {
        when(carteira.findByPetIdOrderByDataAplicacaoDescIdDesc(1L))
                .thenReturn(List.of(new CarteiraVacina(rex, raiva, HOJE.minusYears(1), proxima, null, null, null)));
        LancamentoVacinaResponse l = service.listar(1L).getFirst();
        assertThat(l.statusDescricao()).isEqualTo(l.status().getDescricao());
        return l.status();
    }

    @Test
    void deveRegistrarVacinaDaMesmaEspecie() {
        LancamentoVacinaResponse r = service.registrar(1L, req(4L, HOJE, HOJE.plusYears(1)));

        assertThat(r.vacina().nome()).isEqualTo("Raiva (Antirrábica Canina)");
        assertThat(r.veterinario()).isEqualTo("Dra. Ana");
        assertThat(r.status()).isEqualTo(StatusVacina.EM_DIA);
        verify(auditoria).registrar(eq("VACINA_PET_REGISTRADA"), eq("pet"), eq(1L), anyMap());
    }

    @Test
    void deveFalharQuandoVacinaForDeOutraEspecie() {
        when(vacinas.findById(6L)).thenReturn(Optional.of(vacina(6L, "V3", gato, true)));

        assertThatThrownBy(() -> service.registrar(1L, req(6L, HOJE, null))).isInstanceOf(ConflictException.class);
        verify(carteira, never()).save(any());
    }

    @Test
    void deveFalharQuandoVacinaEstiverInativa() {
        when(vacinas.findById(5L)).thenReturn(Optional.of(vacina(5L, "Antiga", cao, false)));

        assertThatThrownBy(() -> service.registrar(1L, req(5L, HOJE, null))).isInstanceOf(ConflictException.class);
    }

    @Test
    void deveFalharQuandoDataAplicacaoForFutura() {
        assertThatThrownBy(() -> service.registrar(1L, req(4L, HOJE.plusDays(1), null)))
                .isInstanceOf(CampoInvalidoException.class)
                .extracting("campo").isEqualTo("dataAplicacao");
    }

    @Test
    void deveFalharQuandoDataProximaForAnteriorAAplicacao() {
        assertThatThrownBy(() -> service.registrar(1L, req(4L, HOJE, HOJE.minusDays(1))))
                .isInstanceOf(CampoInvalidoException.class)
                .extracting("campo").isEqualTo("dataProxima");
    }

    @Test
    void deveCalcularStatusVencida() {
        assertThat(statusCom(HOJE.minusDays(1))).isEqualTo(StatusVacina.VENCIDA);
    }

    @Test
    void deveCalcularStatusVenceEmTrintaDias() {
        assertThat(statusCom(HOJE.plusDays(30))).isEqualTo(StatusVacina.VENCE_EM_30_DIAS);
    }

    @Test
    void deveConsiderarHojeComoVenceEmTrintaDias() {
        assertThat(statusCom(HOJE)).isEqualTo(StatusVacina.VENCE_EM_30_DIAS);
    }

    @Test
    void deveCalcularStatusEmDia() {
        assertThat(statusCom(HOJE.plusDays(31))).isEqualTo(StatusVacina.EM_DIA);
    }

    @Test
    void deveCalcularStatusSemProximaDose() {
        assertThat(statusCom(null)).isEqualTo(StatusVacina.SEM_PROXIMA_DOSE);
    }

    @Test
    void deveRemoverLancamentoDoPet() {
        CarteiraVacina lancamento = new CarteiraVacina(rex, raiva, HOJE, null, null, null, null);
        when(carteira.findById(9L)).thenReturn(Optional.of(lancamento));

        service.remover(1L, 9L);

        verify(carteira).delete(lancamento);
        verify(auditoria).registrar(eq("VACINA_PET_REMOVIDA"), eq("pet"), eq(1L), anyMap());
    }

    @Test
    void deveFalharAoRemoverLancamentoDeOutroPet() {
        when(carteira.findById(9L)).thenReturn(Optional.of(new CarteiraVacina(rex, raiva, HOJE, null, null, null, null)));

        assertThatThrownBy(() -> service.remover(2L, 9L)).isInstanceOf(NotFoundException.class);
        verify(carteira, never()).delete(any());
    }
}
