package br.com.tecnodreamer.petconnect.agendamento.domain;

import br.com.tecnodreamer.petconnect.shared.error.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransicaoAgendamentoTest {

    @ParameterizedTest(name = "{0} -> {1} permitido")
    @CsvSource({
            "AGENDADO, CONFIRMADO", "AGENDADO, CANCELADO", "AGENDADO, NAO_COMPARECEU",
            "CONFIRMADO, EM_ANDAMENTO", "CONFIRMADO, CANCELADO", "CONFIRMADO, NAO_COMPARECEU",
            "EM_ANDAMENTO, CONCLUIDO", "EM_ANDAMENTO, CANCELADO"})
    void transicoesPermitidas(StatusAgendamento de, StatusAgendamento para) {
        assertThat(de.podeIrPara(para)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1} proibido")
    @CsvSource({
            "AGENDADO, CONCLUIDO", "AGENDADO, EM_ANDAMENTO", "CONFIRMADO, CONCLUIDO", "CONFIRMADO, AGENDADO",
            "EM_ANDAMENTO, NAO_COMPARECEU", "EM_ANDAMENTO, AGENDADO", "CANCELADO, AGENDADO"})
    void transicoesProibidas(StatusAgendamento de, StatusAgendamento para) {
        assertThat(de.podeIrPara(para)).isFalse();
    }

    @ParameterizedTest(name = "{0} é terminal")
    @EnumSource(value = StatusAgendamento.class, names = {"CONCLUIDO", "CANCELADO", "NAO_COMPARECEU"})
    void estadosTerminaisNaoVaoParaNenhumOutro(StatusAgendamento terminal) {
        assertThat(terminal.terminal()).isTrue();
        for (StatusAgendamento qualquer : StatusAgendamento.values()) {
            assertThat(terminal.podeIrPara(qualquer)).isFalse();
        }
    }

    @Test
    void transicaoInvalidaGeraConflitoComMensagem() {
        assertThatThrownBy(() -> StatusAgendamento.AGENDADO.exigirTransicaoPara(StatusAgendamento.CONCLUIDO))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A transição de status AGENDADO para CONCLUIDO não é permitida.");
    }

    @Test
    void somenteEstadosAtivosOcupamAgendaESaoEditaveisOsDoisPrimeiros() {
        assertThat(StatusAgendamento.ATIVOS).containsExactlyInAnyOrder(
                StatusAgendamento.AGENDADO, StatusAgendamento.CONFIRMADO, StatusAgendamento.EM_ANDAMENTO);
        assertThat(StatusAgendamento.AGENDADO.editavel()).isTrue();
        assertThat(StatusAgendamento.CONFIRMADO.editavel()).isTrue();
        assertThat(StatusAgendamento.EM_ANDAMENTO.editavel()).isFalse();
    }
}
