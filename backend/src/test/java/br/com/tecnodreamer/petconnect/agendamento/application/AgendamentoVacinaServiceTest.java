package br.com.tecnodreamer.petconnect.agendamento.application;

import br.com.tecnodreamer.petconnect.venda.domain.FormaPagamento;
import br.com.tecnodreamer.petconnect.financeiro.application.FinanceiroService;
import br.com.tecnodreamer.petconnect.financeiro.application.CaixaService;
import br.com.tecnodreamer.petconnect.agendamento.domain.Agendamento;
import br.com.tecnodreamer.petconnect.agendamento.domain.ServicoRealizado;
import br.com.tecnodreamer.petconnect.agendamento.domain.StatusAgendamento;
import br.com.tecnodreamer.petconnect.agendamento.infrastructure.AgendamentoRepository;
import br.com.tecnodreamer.petconnect.agendamento.infrastructure.ServicoRealizadoRepository;
import br.com.tecnodreamer.petconnect.agendamento.web.AgendamentoRequest;
import br.com.tecnodreamer.petconnect.agendamento.web.AgendamentoResponse;
import br.com.tecnodreamer.petconnect.agendamento.web.StatusRequest;
import br.com.tecnodreamer.petconnect.funcionario.infrastructure.FuncionarioRepository;
import br.com.tecnodreamer.petconnect.pet.application.CarteiraVacinaService;
import br.com.tecnodreamer.petconnect.pet.domain.Pet;
import br.com.tecnodreamer.petconnect.pet.domain.Porte;
import br.com.tecnodreamer.petconnect.pet.infrastructure.PetRepository;
import br.com.tecnodreamer.petconnect.servico.domain.Servico;
import br.com.tecnodreamer.petconnect.servico.infrastructure.ServicoRepository;
import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;
import br.com.tecnodreamer.petconnect.shared.error.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static br.com.tecnodreamer.petconnect.agendamento.AgendaFixtures.AGORA;
import static br.com.tecnodreamer.petconnect.agendamento.AgendaFixtures.SEGUNDA_9H;
import static br.com.tecnodreamer.petconnect.agendamento.AgendaFixtures.agendamento;
import static br.com.tecnodreamer.petconnect.agendamento.AgendaFixtures.banho;
import static br.com.tecnodreamer.petconnect.agendamento.AgendaFixtures.pet;
import static br.com.tecnodreamer.petconnect.agendamento.domain.StatusAgendamento.AGENDADO;
import static br.com.tecnodreamer.petconnect.agendamento.domain.StatusAgendamento.CANCELADO;
import static br.com.tecnodreamer.petconnect.agendamento.domain.StatusAgendamento.CONCLUIDO;
import static br.com.tecnodreamer.petconnect.agendamento.domain.StatusAgendamento.CONFIRMADO;
import static br.com.tecnodreamer.petconnect.agendamento.domain.StatusAgendamento.EM_ANDAMENTO;
import static br.com.tecnodreamer.petconnect.agendamento.domain.StatusAgendamento.NAO_COMPARECEU;
import static br.com.tecnodreamer.petconnect.pet.PetFixtures.CLOCK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.tecnodreamer.petconnect.pet.web.VacinaLancamentoRequest;
import java.time.LocalDate;

import static br.com.tecnodreamer.petconnect.agendamento.AgendaFixtures.vacinacao;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgendamentoVacinaServiceTest {

    @Mock AgendamentoRepository agendamentos;
    @Mock PetRepository pets;
    @Mock ServicoRepository servicos;
    @Mock FuncionarioRepository funcionarios;
    @Mock ServicoRealizadoRepository realizados;
    @Mock CarteiraVacinaService carteira;
    @Mock AuditoriaService auditoria;
    @Mock CaixaService caixas;
    @Mock FinanceiroService financeiro;

    AgendamentoService service;
    final Pet rex = pet(1L, Porte.GRANDE);
    final VacinaLancamentoRequest dose = new VacinaLancamentoRequest(4L, LocalDate.of(2026, 9, 25),
            LocalDate.of(2027, 9, 25), "Dra. Ana", "L1", null);

    @BeforeEach
    void setUp() {
        service = new AgendamentoService(agendamentos, pets, servicos, funcionarios, realizados, carteira, auditoria, caixas, financeiro, CLOCK);
        when(realizados.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(agendamentos.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Agendamento emAndamento(Servico servico) {
        Agendamento a = agendamento(10L, rex, servico, AGORA.minusMinutes(10), EM_ANDAMENTO);
        when(agendamentos.findWithDetalhesById(10L)).thenReturn(Optional.of(a));
        return a;
    }

    private static StatusRequest concluir(VacinaLancamentoRequest vacina) {
        return new StatusRequest(CONCLUIDO, null, null, null, vacina, FormaPagamento.PIX);
    }

    @Test
    void deveConcluirVacinacaoELancarCarteira() {
        Agendamento a = emAndamento(vacinacao());

        service.alterarStatus(10L, concluir(dose), Set.of("SERVICO"), 1L);

        verify(carteira).registrar(1L, dose, 1L);
        verify(realizados).saveAndFlush(any());
        assertThat(a.getStatus()).isEqualTo(CONCLUIDO);
    }

    @Test
    void deveFalharVacinacaoSemPayloadDeVacina() {
        emAndamento(vacinacao());

        assertThatThrownBy(() -> service.alterarStatus(10L, concluir(null), Set.of("ADMIN"), 1L))
                .isInstanceOf(CampoInvalidoException.class).extracting("campo").isEqualTo("vacina");
        verify(realizados, never()).saveAndFlush(any());
    }

    @Test
    void deveFalharQuandoVacinaNaoPertenceAEspecie() {
        Agendamento a = emAndamento(vacinacao());
        when(carteira.registrar(1L, dose, 1L))
                .thenThrow(new ConflictException("A vacina selecionada não é indicada para a espécie deste pet."));

        assertThatThrownBy(() -> service.alterarStatus(10L, concluir(dose), Set.of("VENDAS"), 1L))
                .isInstanceOf(ConflictException.class).hasMessageContaining("espécie");
        assertThat(a.getStatus()).isEqualTo(EM_ANDAMENTO);
    }

    @Test
    void deveDesfazerConclusaoQuandoLancamentoDaVacinaFalhar() {
        Agendamento a = emAndamento(vacinacao());
        when(carteira.registrar(1L, dose, 1L))
                .thenThrow(new CampoInvalidoException("dataAplicacao", "A data de aplicação não pode ser futura."));

        assertThatThrownBy(() -> service.alterarStatus(10L, concluir(dose), Set.of("VENDAS"), 1L))
                .isInstanceOf(CampoInvalidoException.class);
        assertThat(a.getStatus()).isEqualTo(EM_ANDAMENTO);
        verify(realizados, never()).saveAndFlush(any());
        verify(agendamentos, never()).saveAndFlush(any());
        verify(auditoria, never()).registrar(eq("AGENDAMENTO_CONCLUIDO"), any(), any(), anyMap());
    }

    @Test
    void deveRejeitarPayloadVacinaParaServicoNaoVacinacao() {
        emAndamento(banho());

        assertThatThrownBy(() -> service.alterarStatus(10L, concluir(dose), Set.of("ADMIN"), 1L))
                .isInstanceOf(CampoInvalidoException.class).extracting("campo").isEqualTo("vacina");
        verify(carteira, never()).registrar(anyLong(), any(), any());
    }
}
