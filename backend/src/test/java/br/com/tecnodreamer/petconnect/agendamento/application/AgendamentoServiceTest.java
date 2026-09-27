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

import org.springframework.security.access.AccessDeniedException;

import static br.com.tecnodreamer.petconnect.agendamento.AgendaFixtures.funcionario;
import static br.com.tecnodreamer.petconnect.agendamento.AgendaFixtures.servico;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgendamentoServiceTest {

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
    Pet mel = pet(1L, Porte.MEDIO);
    final Servico banho = banho();

    @BeforeEach
    void setUp() {
        service = new AgendamentoService(agendamentos, pets, servicos, funcionarios, realizados, carteira, auditoria, caixas, financeiro, CLOCK);
        when(realizados.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pets.travarParaAgendamento(1L)).thenAnswer(inv -> Optional.of(mel));
        when(servicos.findById(1L)).thenReturn(Optional.of(banho));
        when(agendamentos.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static AgendamentoRequest req(LocalDateTime dataHora) {
        return new AgendamentoRequest(1L, 1L, null, dataHora, " Shampoo hipoalergênico ");
    }

    private Agendamento existente(StatusAgendamento status, LocalDateTime dataHora) {
        Agendamento a = agendamento(10L, mel, banho, dataHora, status);
        when(agendamentos.findWithDetalhesById(10L)).thenReturn(Optional.of(a));
        return a;
    }

    // ------------------------------------------------------------ preço

    @Test
    void deveCriarAgendamentoComPrecoPorPorte() {
        AgendamentoResponse r = service.criar(req(SEGUNDA_9H));

        assertThat(r.totalEstimado()).isEqualByComparingTo("55.00");
        assertThat(r.status()).isEqualTo(AGENDADO);
        assertThat(r.fimPrevisto()).isEqualTo(SEGUNDA_9H.plusMinutes(60));
        assertThat(r.observacoes()).isEqualTo("Shampoo hipoalergênico");
        verify(auditoria).registrar(eq("AGENDAMENTO_CRIADO"), eq("agendamento"), any(), anyMap());
    }

    @Test
    void deveUsarPrecoPadraoQuandoPetNaoTemPorte() {
        mel = pet(1L, null);

        assertThat(service.criar(req(SEGUNDA_9H)).totalEstimado()).isEqualByComparingTo("50.00");
    }

    @Test
    void deveUsarPrecoPadraoQuandoNaoExistePrecoParaOPorte() {
        mel = pet(1L, Porte.PEQUENO);

        assertThat(service.criar(req(SEGUNDA_9H)).totalEstimado()).isEqualByComparingTo("50.00");
    }

    @Test
    void deveCongelarTotalEstimadoNaCriacao() {
        ArgumentCaptor<Agendamento> captor = ArgumentCaptor.forClass(Agendamento.class);
        service.criar(req(SEGUNDA_9H));
        verify(agendamentos).saveAndFlush(captor.capture());

        banho.atualizar("Banho", null, new BigDecimal("99.00"), 60, Map.of(Porte.MEDIO, new BigDecimal("99.00")));

        assertThat(captor.getValue().getTotalEstimado()).isEqualByComparingTo("55.00");
    }

    @Test
    void deveRecalcularPrecoAoEditarAgendamento() {
        existente(AGENDADO, SEGUNDA_9H);
        Servico tosa = servico(2L, "Tosa", "60.00", 90, Map.of(Porte.MEDIO, new BigDecimal("80.00")));
        when(servicos.findById(2L)).thenReturn(Optional.of(tosa));

        AgendamentoResponse r = service.atualizar(10L, new AgendamentoRequest(1L, 2L, null, SEGUNDA_9H, null));

        assertThat(r.totalEstimado()).isEqualByComparingTo("80.00");
        assertThat(r.servico().nome()).isEqualTo("Tosa");
        verify(auditoria).registrar(eq("AGENDAMENTO_ATUALIZADO"), eq("agendamento"), eq(10L), anyMap());
    }

    // ------------------------------------------------------------ validações

    @Test
    void deveFalharComPetInativo() {
        mel.desativar();

        assertThatThrownBy(() -> service.criar(req(SEGUNDA_9H)))
                .isInstanceOf(CampoInvalidoException.class).extracting("campo").isEqualTo("petId");
    }

    @Test
    void deveFalharComServicoInativo() {
        banho.desativar();

        assertThatThrownBy(() -> service.criar(req(SEGUNDA_9H)))
                .isInstanceOf(CampoInvalidoException.class).extracting("campo").isEqualTo("servicoId");
    }

    @Test
    void deveFalharComDataHoraNoPassado() {
        assertThatThrownBy(() -> service.criar(req(AGORA)))
                .isInstanceOf(CampoInvalidoException.class).extracting("campo").isEqualTo("dataHora");
        verify(agendamentos, never()).saveAndFlush(any());
    }

    // ------------------------------------------------------------ conflito

    @Test
    void deveDetectarConflitoDoMesmoPet() {
        when(agendamentos.candidatosPorPet(eq(1L), any(), any(), any()))
                .thenReturn(List.of(agendamento(20L, mel, banho, SEGUNDA_9H, CONFIRMADO)));

        assertThatThrownBy(() -> service.criar(req(SEGUNDA_9H.plusMinutes(30))))
                .isInstanceOf(ConflictException.class)
                .hasMessage("O pet já possui um agendamento que se sobrepõe a este horário.");
    }

    @Test
    void devePermitirAgendamentoExatamenteAposFimDoAnterior() {
        when(agendamentos.candidatosPorPet(eq(1L), any(), any(), any()))
                .thenReturn(List.of(agendamento(20L, mel, banho, SEGUNDA_9H, AGENDADO)));

        assertThat(service.criar(req(SEGUNDA_9H.plusMinutes(60))).status()).isEqualTo(AGENDADO);
    }

    @Test
    void deveDetectarConflitoDoMesmoFuncionario() {
        when(funcionarios.findById(3L)).thenReturn(Optional.of(funcionario(3L)));
        Pet outroPet = pet(2L, null);
        when(agendamentos.candidatosPorFuncionario(eq(3L), any(), any(), any()))
                .thenReturn(List.of(agendamento(21L, outroPet, banho, SEGUNDA_9H, AGENDADO)));

        assertThatThrownBy(() -> service.criar(new AgendamentoRequest(1L, 1L, 3L, SEGUNDA_9H.plusMinutes(59), null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("O funcionário já possui um agendamento que se sobrepõe a este horário.");
    }

    @Test
    void deveIgnorarConflitoDoProprioAgendamentoNaEdicao() {
        Agendamento proprio = existente(AGENDADO, SEGUNDA_9H);
        when(agendamentos.candidatosPorPet(eq(1L), any(), any(), any())).thenReturn(List.of(proprio));

        AgendamentoResponse r = service.atualizar(10L, req(SEGUNDA_9H.plusMinutes(30)));

        assertThat(r.dataHora()).isEqualTo(SEGUNDA_9H.plusMinutes(30));
    }

    // ------------------------------------------------------------ edição

    @Test
    void devePermitirEditarAgendado() {
        existente(AGENDADO, SEGUNDA_9H);
        assertThat(service.atualizar(10L, req(SEGUNDA_9H.plusDays(1))).dataHora()).isEqualTo(SEGUNDA_9H.plusDays(1));
    }

    @Test
    void devePermitirEditarConfirmado() {
        existente(CONFIRMADO, SEGUNDA_9H);
        AgendamentoResponse r = service.atualizar(10L, req(SEGUNDA_9H.plusDays(1)));
        assertThat(r.status()).isEqualTo(CONFIRMADO);
    }

    @Test
    void deveFalharAoEditarEmAndamento() {
        existente(EM_ANDAMENTO, SEGUNDA_9H);
        assertThatThrownBy(() -> service.atualizar(10L, req(SEGUNDA_9H.plusDays(1)))).isInstanceOf(ConflictException.class);
    }

    // ------------------------------------------------------------ transições

    @Test
    void deveConfirmarAgendamento() {
        Agendamento a = existente(AGENDADO, SEGUNDA_9H);

        service.alterarStatus(10L, StatusRequest.simples(CONFIRMADO), Set.of("VENDAS"), 1L);

        assertThat(a.getStatus()).isEqualTo(CONFIRMADO);
        verify(auditoria).registrar(eq("AGENDAMENTO_STATUS_ALTERADO"), eq("agendamento"), eq(10L),
                argThat(d -> "AGENDADO".equals(d.get("statusAnterior")) && "CONFIRMADO".equals(d.get("statusNovo"))));
    }

    @Test
    void servicoNaoPodeConfirmar() {
        Agendamento a = existente(AGENDADO, SEGUNDA_9H);

        assertThatThrownBy(() -> service.alterarStatus(10L, StatusRequest.simples(CONFIRMADO), Set.of("SERVICO"), 1L))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(a.getStatus()).isEqualTo(AGENDADO);
    }

    @Test
    void deveIniciarAgendamentoConfirmado() {
        Agendamento a = existente(CONFIRMADO, SEGUNDA_9H);

        service.alterarStatus(10L, StatusRequest.simples(EM_ANDAMENTO), Set.of("SERVICO"), 1L);

        assertThat(a.getStatus()).isEqualTo(EM_ANDAMENTO);
    }

    @Test
    void deveFalharTransicaoAgendadoParaConcluido() {
        existente(AGENDADO, SEGUNDA_9H);

        assertThatThrownBy(() -> service.alterarStatus(10L, StatusRequest.simples(CONCLUIDO), Set.of("ADMIN"), 1L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A transição de status AGENDADO para CONCLUIDO não é permitida.");
        verify(realizados, never()).saveAndFlush(any());
    }

    @Test
    void deveCancelarAgendamentoComMotivo() {
        Agendamento a = existente(CONFIRMADO, SEGUNDA_9H);

        service.alterarStatus(10L, new StatusRequest(CANCELADO, " Tutor viajou ", null, null, null, null), Set.of("VENDAS"), 1L);

        assertThat(a.getStatus()).isEqualTo(CANCELADO);
        assertThat(a.getObservacoes()).isEqualTo("[CANCELAMENTO] Tutor viajou");
        verify(auditoria).registrar(eq("AGENDAMENTO_CANCELADO"), eq("agendamento"), eq(10L),
                argThat(d -> "Tutor viajou".equals(d.get("motivo"))));
    }

    @Test
    void deveExigirMotivoParaCancelar() {
        existente(AGENDADO, SEGUNDA_9H);

        assertThatThrownBy(() -> service.alterarStatus(10L, StatusRequest.simples(CANCELADO), Set.of("ADMIN"), 1L))
                .isInstanceOf(CampoInvalidoException.class).extracting("campo").isEqualTo("motivo");
    }

    @Test
    void deveMarcarNaoComparecimentoAposHorario() {
        Agendamento a = existente(AGENDADO, AGORA.minusHours(1));

        service.alterarStatus(10L, new StatusRequest(NAO_COMPARECEU, "Não atendeu", null, null, null, null), Set.of("SERVICO"), 1L);

        assertThat(a.getStatus()).isEqualTo(NAO_COMPARECEU);
        assertThat(a.getObservacoes()).contains("[NÃO COMPARECIMENTO] Não atendeu");
        verify(auditoria).registrar(eq("AGENDAMENTO_NAO_COMPARECEU"), eq("agendamento"), eq(10L), anyMap());
    }

    @Test
    void deveFalharNaoComparecimentoAntesDoHorario() {
        existente(CONFIRMADO, AGORA.plusMinutes(1));

        assertThatThrownBy(() -> service.alterarStatus(10L, StatusRequest.simples(NAO_COMPARECEU), Set.of("VENDAS"), 1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ainda não atingiu o horário previsto");
    }

    // ------------------------------------------------------------ conclusão

    private ServicoRealizado concluirCom(BigDecimal valorFinal) {
        existente(EM_ANDAMENTO, AGORA.minusHours(1));
        service.alterarStatus(10L, new StatusRequest(CONCLUIDO, null, valorFinal, " Tudo certo ", null, FormaPagamento.PIX), Set.of("SERVICO"), 1L);
        ArgumentCaptor<ServicoRealizado> captor = ArgumentCaptor.forClass(ServicoRealizado.class);
        verify(realizados).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    @Test
    void deveConcluirECriarServicoRealizado() {
        ServicoRealizado r = concluirCom(new BigDecimal("60.00"));

        assertThat(r.getAgendamento().getStatus()).isEqualTo(CONCLUIDO);
        assertThat(r.getDataServico()).isEqualTo(AGORA);
        assertThat(r.getObservacoes()).isEqualTo("Tudo certo");
        verify(carteira, never()).registrar(anyLong(), any());
        verify(auditoria).registrar(eq("AGENDAMENTO_CONCLUIDO"), eq("agendamento"), eq(10L),
                argThat(d -> new BigDecimal("60.00").equals(d.get("valorFinal"))));
    }

    @Test
    void deveUsarTotalEstimadoQuandoValorFinalNaoInformado() {
        assertThat(concluirCom(null).getValor()).isEqualByComparingTo("55.00");
    }

    @Test
    void deveUsarValorFinalInformadoNaConclusao() {
        ServicoRealizado r = concluirCom(new BigDecimal("40.00"));

        assertThat(r.getValor()).isEqualByComparingTo("40.00");
        assertThat(r.getAgendamento().getTotalEstimado()).isEqualByComparingTo("55.00");
    }

    @Test
    void deveFalharAoConcluirDuasVezes() {
        existente(EM_ANDAMENTO, AGORA);
        when(realizados.existsByAgendamentoId(10L)).thenReturn(true);

        assertThatThrownBy(() -> service.alterarStatus(10L, StatusRequest.simples(CONCLUIDO), Set.of("ADMIN"), 1L))
                .isInstanceOf(ConflictException.class);
        verify(realizados, never()).saveAndFlush(any());

        Agendamento concluido = existente(CONCLUIDO, AGORA);
        assertThatThrownBy(() -> service.alterarStatus(10L, StatusRequest.simples(CONCLUIDO), Set.of("ADMIN"), 1L))
                .isInstanceOf(ConflictException.class);
        assertThat(concluido.getStatus()).isEqualTo(CONCLUIDO);
    }

    @Test
    void valorFinalForaDaConclusaoERejeitado() {
        existente(AGENDADO, SEGUNDA_9H);

        assertThatThrownBy(() -> service.alterarStatus(10L,
                new StatusRequest(CONFIRMADO, null, BigDecimal.TEN, null, null, null), Set.of("ADMIN"), 1L))
                .isInstanceOf(CampoInvalidoException.class);
    }

    // ------------------------------------------------------------ etapa 06: financeiro na conclusão

    @Test
    void conclusaoDeveExigirFormaDePagamento() {
        existente(EM_ANDAMENTO, AGORA);

        assertThatThrownBy(() -> service.alterarStatus(10L, StatusRequest.simples(CONCLUIDO), Set.of("SERVICO"), 1L))
                .isInstanceOf(CampoInvalidoException.class).extracting("campo").isEqualTo("formaPagamento");
        verify(realizados, never()).saveAndFlush(any());
        verify(financeiro, never()).registrarServico(any(), any(), any(), any());
    }

    @Test
    void conclusaoSemCaixaDeveFalharSemEfeitos() {
        existente(EM_ANDAMENTO, AGORA);
        org.mockito.Mockito.doThrow(new ConflictException(FinanceiroService.CAIXA_AUSENTE)).when(caixas).exigirAberto(1L);

        assertThatThrownBy(() -> service.alterarStatus(10L,
                new StatusRequest(CONCLUIDO, null, null, null, null, FormaPagamento.PIX), Set.of("SERVICO"), 1L))
                .isInstanceOf(ConflictException.class).hasMessage(FinanceiroService.CAIXA_AUSENTE);
        verify(realizados, never()).saveAndFlush(any());
        verify(auditoria, never()).registrar(eq("AGENDAMENTO_CONCLUIDO"), any(), any(), any());
    }

    @Test
    void conclusaoDeveRegistrarReceitaNoCaixaDoUsuario() {
        ServicoRealizado r = concluirCom(new BigDecimal("60.00"));

        verify(caixas).travarAberto(1L);
        verify(financeiro).registrarServico(eq(r), eq(FormaPagamento.PIX), any(), eq(1L));
    }

    @Test
    void formaDePagamentoForaDaConclusaoERejeitada() {
        existente(AGENDADO, SEGUNDA_9H);

        assertThatThrownBy(() -> service.alterarStatus(10L,
                new StatusRequest(CONFIRMADO, null, null, null, null, FormaPagamento.PIX), Set.of("ADMIN"), 1L))
                .isInstanceOf(CampoInvalidoException.class).extracting("campo").isEqualTo("formaPagamento");
    }
}
