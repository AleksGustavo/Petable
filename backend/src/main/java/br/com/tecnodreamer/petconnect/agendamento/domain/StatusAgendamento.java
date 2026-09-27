package br.com.tecnodreamer.petconnect.agendamento.domain;

import br.com.tecnodreamer.petconnect.shared.error.ConflictException;

import java.util.EnumSet;
import java.util.Set;

/** Máquina de estados do agendamento — o único lugar que decide quais transições existem. */
public enum StatusAgendamento {

    AGENDADO("Agendado"),
    CONFIRMADO("Confirmado"),
    EM_ANDAMENTO("Em andamento"),
    CONCLUIDO("Concluído"),
    CANCELADO("Cancelado"),
    NAO_COMPARECEU("Não compareceu");

    /** Estados que ocupam a agenda (entram na verificação de conflito). */
    public static final Set<StatusAgendamento> ATIVOS = EnumSet.of(AGENDADO, CONFIRMADO, EM_ANDAMENTO);

    private final String descricao;

    StatusAgendamento(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    public Set<StatusAgendamento> proximosPermitidos() {
        return switch (this) {
            case AGENDADO -> EnumSet.of(CONFIRMADO, CANCELADO, NAO_COMPARECEU);
            case CONFIRMADO -> EnumSet.of(EM_ANDAMENTO, CANCELADO, NAO_COMPARECEU);
            case EM_ANDAMENTO -> EnumSet.of(CONCLUIDO, CANCELADO);
            case CONCLUIDO, CANCELADO, NAO_COMPARECEU -> EnumSet.noneOf(StatusAgendamento.class);
        };
    }

    public boolean podeIrPara(StatusAgendamento novo) {
        return proximosPermitidos().contains(novo);
    }

    public boolean terminal() {
        return proximosPermitidos().isEmpty();
    }

    public boolean editavel() {
        return this == AGENDADO || this == CONFIRMADO;
    }

    public void exigirTransicaoPara(StatusAgendamento novo) {
        if (!podeIrPara(novo)) {
            throw new ConflictException("A transição de status " + this + " para " + novo + " não é permitida.");
        }
    }
}
