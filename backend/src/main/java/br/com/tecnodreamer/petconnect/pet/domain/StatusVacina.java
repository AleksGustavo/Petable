package br.com.tecnodreamer.petconnect.pet.domain;

import java.time.LocalDate;

/** Status de um lançamento na carteira, calculado a cada leitura (nunca persistido). */
public enum StatusVacina {

    VENCIDA("Vencida"),
    VENCE_EM_30_DIAS("Vence em até 30 dias"),
    EM_DIA("Em dia"),
    SEM_PROXIMA_DOSE("Sem próxima dose");

    static final int JANELA_DIAS = 30;

    private final String descricao;

    StatusVacina(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    public static StatusVacina calcular(LocalDate dataProxima, LocalDate hoje) {
        if (dataProxima == null) {
            return SEM_PROXIMA_DOSE;
        }
        if (dataProxima.isBefore(hoje)) {
            return VENCIDA;
        }
        return dataProxima.isAfter(hoje.plusDays(JANELA_DIAS)) ? EM_DIA : VENCE_EM_30_DIAS;
    }
}
