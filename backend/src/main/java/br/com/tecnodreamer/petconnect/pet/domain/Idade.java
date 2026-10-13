package br.com.tecnodreamer.petconnect.pet.domain;

import java.time.LocalDate;
import java.time.Period;

/** Idade calculada a partir do nascimento — nunca persistida. */
public record Idade(int anos, int meses, String descricao) {

    /** {@code null} quando o nascimento não é conhecido. */
    public static Idade calcular(LocalDate nascimento, LocalDate hoje) {
        if (nascimento == null) {
            return null;
        }
        Period p = Period.between(nascimento, hoje);
        int anos = Math.max(p.getYears(), 0);
        int meses = Math.max(p.getMonths(), 0);
        return new Idade(anos, meses, descrever(anos, meses));
    }

    private static String descrever(int anos, int meses) {
        String a = anos == 1 ? "1 ano" : anos + " anos";
        String m = meses == 1 ? "1 mês" : meses + " meses";
        if (anos > 0 && meses > 0) {
            return a + " e " + m;
        }
        if (anos > 0) {
            return a;
        }
        return meses > 0 ? m : "menos de 1 mês";
    }
}
