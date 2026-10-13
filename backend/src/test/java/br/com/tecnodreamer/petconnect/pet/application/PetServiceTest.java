package br.com.tecnodreamer.petconnect.pet.application;

import br.com.tecnodreamer.petconnect.cliente.domain.Cliente;
import br.com.tecnodreamer.petconnect.cliente.infrastructure.ClienteRepository;
import br.com.tecnodreamer.petconnect.pet.domain.Especie;
import br.com.tecnodreamer.petconnect.pet.domain.Pet;
import br.com.tecnodreamer.petconnect.pet.domain.Porte;
import br.com.tecnodreamer.petconnect.pet.domain.Raca;
import br.com.tecnodreamer.petconnect.pet.infrastructure.EspecieRepository;
import br.com.tecnodreamer.petconnect.pet.infrastructure.PetRepository;
import br.com.tecnodreamer.petconnect.pet.infrastructure.RacaRepository;
import br.com.tecnodreamer.petconnect.pet.web.PetRequest;
import br.com.tecnodreamer.petconnect.pet.web.PetResponse;
import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;
import br.com.tecnodreamer.petconnect.shared.error.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static br.com.tecnodreamer.petconnect.pet.PetFixtures.CLOCK;
import static br.com.tecnodreamer.petconnect.pet.PetFixtures.cliente;
import static br.com.tecnodreamer.petconnect.pet.PetFixtures.especie;
import static br.com.tecnodreamer.petconnect.pet.PetFixtures.pet;
import static br.com.tecnodreamer.petconnect.pet.PetFixtures.raca;
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
class PetServiceTest {

    @Mock PetRepository pets;
    @Mock ClienteRepository clientes;
    @Mock EspecieRepository especies;
    @Mock RacaRepository racas;
    @Mock AuditoriaService auditoria;

    PetService service;

    final Especie cao = especie(1L, "Cão");
    final Especie gato = especie(2L, "Gato");
    final Raca labrador = raca(10L, "Labrador", cao);
    final Cliente tutor = cliente(7L);

    @BeforeEach
    void setUp() {
        service = new PetService(pets, clientes, especies, racas, auditoria, CLOCK);
        when(clientes.findById(7L)).thenReturn(Optional.of(tutor));
        when(especies.findById(1L)).thenReturn(Optional.of(cao));
        when(especies.findById(2L)).thenReturn(Optional.of(gato));
        when(racas.findById(10L)).thenReturn(Optional.of(labrador));
        when(pets.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static PetRequest request(Long clienteId, Long especieId, Long racaId, LocalDate nascimento) {
        return new PetRequest(clienteId, "  Thor ", especieId, racaId, nascimento, new BigDecimal("32.50"),
                " Caramelo ", Porte.GRANDE, true, " ");
    }

    @Test
    void deveCriarPetComDadosValidos() {
        PetResponse r = service.criar(request(7L, 1L, 10L, LocalDate.of(2023, 5, 20)));

        assertThat(r.nome()).isEqualTo("Thor");
        assertThat(r.tutor().id()).isEqualTo(7L);
        assertThat(r.especie().nome()).isEqualTo("Cão");
        assertThat(r.raca().nome()).isEqualTo("Labrador");
        assertThat(r.cor()).isEqualTo("Caramelo");
        assertThat(r.observacoes()).isNull();
        assertThat(r.castrado()).isTrue();
        verify(auditoria).registrar(eq("PET_CRIADO"), eq("pet"), any(), anyMap());
    }

    @Test
    void deveFalharAoCriarPetComClienteInexistente() {
        when(clientes.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criar(request(99L, 1L, null, null)))
                .isInstanceOf(CampoInvalidoException.class)
                .extracting("campo").isEqualTo("clienteId");
        verify(pets, never()).saveAndFlush(any());
    }

    @Test
    void deveFalharAoCriarPetComClienteInativo() {
        tutor.inativar();

        assertThatThrownBy(() -> service.criar(request(7L, 1L, null, null)))
                .isInstanceOf(CampoInvalidoException.class)
                .extracting("campo").isEqualTo("clienteId");
    }

    @Test
    void deveFalharAoCriarPetComEspecieInexistente() {
        when(especies.findById(50L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criar(request(7L, 50L, null, null)))
                .isInstanceOf(CampoInvalidoException.class)
                .extracting("campo").isEqualTo("especieId");
    }

    @Test
    void deveFalharQuandoRacaNaoPertenceAEspecie() {
        assertThatThrownBy(() -> service.criar(request(7L, 2L, 10L, null)))
                .isInstanceOf(ConflictException.class);
        verify(pets, never()).saveAndFlush(any());
    }

    @Test
    void deveAtualizarPet() {
        Pet existente = pet(5L, tutor, cao, null, null);
        when(pets.findWithDetalhesById(5L)).thenReturn(Optional.of(existente));

        PetResponse r = service.atualizar(5L, request(7L, 1L, 10L, null));

        assertThat(r.nome()).isEqualTo("Thor");
        assertThat(existente.getRaca()).isSameAs(labrador);
        verify(auditoria).registrar(eq("PET_ATUALIZADO"), eq("pet"), eq(5L), anyMap());
    }

    @Test
    void deveFalharAoAlterarEspecieMantendoRacaIncompativel() {
        Pet existente = pet(5L, tutor, cao, labrador, null);
        when(pets.findWithDetalhesById(5L)).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> service.atualizar(5L, request(7L, 2L, 10L, null)))
                .isInstanceOf(ConflictException.class);
        assertThat(existente.getEspecie()).isSameAs(cao);
    }

    @Test
    void deveDesativarPet() {
        Pet existente = pet(5L, tutor, cao, null, null);
        when(pets.findWithDetalhesById(5L)).thenReturn(Optional.of(existente));

        service.desativar(5L);

        assertThat(existente.isAtivo()).isFalse();
        verify(auditoria).registrar(eq("PET_DESATIVADO"), eq("pet"), eq(5L), anyMap());
    }

    @Test
    void deveCalcularIdadeEmAnosEMeses() {
        when(pets.findWithDetalhesById(5L)).thenReturn(Optional.of(pet(5L, tutor, cao, null, LocalDate.of(2021, 4, 10))));

        PetResponse r = service.buscar(5L);

        assertThat(r.idade().anos()).isEqualTo(5);
        assertThat(r.idade().meses()).isEqualTo(5);
        assertThat(r.idade().descricao()).isEqualTo("5 anos e 5 meses");
    }

    @Test
    void deveRetornarIdadeNulaSemDataNascimento() {
        when(pets.findWithDetalhesById(5L)).thenReturn(Optional.of(pet(5L, tutor, cao, null, null)));

        assertThat(service.buscar(5L).idade()).isNull();
    }

    @Test
    void deveFalharComNascimentoFuturo() {
        assertThatThrownBy(() -> service.criar(request(7L, 1L, null, LocalDate.of(2026, 9, 26))))
                .isInstanceOf(CampoInvalidoException.class)
                .extracting("campo").isEqualTo("dataNascimento");
    }
}
