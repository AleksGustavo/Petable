package br.com.tecnodreamer.petconnect.relatorio.application;

import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Período dos relatórios (DA-02): datas locais de São Paulo, obrigatórias,
 * {@code fim >= inicio} e no máximo 366 dias. Para colunas DATETIME o filtro é
 * semiaberto [inicio 00:00, fim+1 00:00).
 */
public record Periodo(LocalDate inicio, LocalDate fim) {

    public static final int MAXIMO_DIAS = 366;

    public static Periodo validar(LocalDate inicio, LocalDate fim) {
        if (inicio == null) {
            throw new CampoInvalidoException("dataInicio", "Informe a data inicial.");
        }
        if (fim == null) {
            throw new CampoInvalidoException("dataFim", "Informe a data final.");
        }
        if (fim.isBefore(inicio)) {
            throw new CampoInvalidoException("dataFim", "A data final não pode ser anterior à inicial.");
        }
        Periodo p = new Periodo(inicio, fim);
        if (p.dias() > MAXIMO_DIAS) {
            throw new CampoInvalidoException("dataFim", "O período pode ter no máximo " + MAXIMO_DIAS + " dias.");
        }
        return p;
    }

    /** Quantidade de dias do período, incluindo as duas pontas. */
    public long dias() {
        return ChronoUnit.DAYS.between(inicio, fim) + 1;
    }

    public LocalDateTime inicioDataHora() {
        return inicio.atStartOfDay();
    }

    public LocalDateTime fimExclusivo() {
        return fim.plusDays(1).atStartOfDay();
    }
}
