package br.com.tecnodreamer.petconnect.migracao.application;

import br.com.tecnodreamer.petconnect.migracao.infrastructure.DestinoMigracao;
import br.com.tecnodreamer.petconnect.shared.validation.Documentos;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Regras de campo repetidas entre migradores. */
final class Campos {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private Campos() {
    }

    /** Telefone só com dígitos; tira o 55 do DDI quando vier junto. Inválido → null. */
    static String telefone(String legado) {
        String t = Documentos.somenteDigitos(legado);
        if (t != null && (t.length() == 12 || t.length() == 13) && t.startsWith("55")) {
            t = t.substring(2);
        }
        return Documentos.telefoneValido(t) ? t : null;
    }

    static String email(String legado) {
        if (legado == null) {
            return null;
        }
        String e = legado.trim().toLowerCase(Locale.ROOT);
        return e.length() <= 120 && EMAIL.matcher(e).matches() ? e : null;
    }

    static String observacao(String atual, String acrescimo) {
        return atual == null ? acrescimo : atual + "\n" + acrescimo;
    }

    static boolean ativo(Linha l, List<String> pendencias) {
        return ConversorLegado.booleano(l.bruto("ativo"), true).anotar(pendencias);
    }

    /** Casa um nome pelo texto normalizado dentro de uma lista do catálogo novo. */
    static Optional<Long> casar(List<DestinoMigracao.Nomeado> catalogo, String nome) {
        String chave = TextoNormalizado.comparacao(nome);
        return catalogo.stream().filter(n -> chave != null && chave.equals(TextoNormalizado.comparacao(n.nome())))
                .map(DestinoMigracao.Nomeado::id).findFirst();
    }
}
