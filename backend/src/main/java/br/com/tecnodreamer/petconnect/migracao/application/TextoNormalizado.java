package br.com.tecnodreamer.petconnect.migracao.application;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

/**
 * Normalização SOMENTE para comparação (DA-20): o texto gravado continua o original.
 * trim → minúsculas → sem acento → espaços simples.
 */
public final class TextoNormalizado {

    /** Sinônimos de espécie comuns no legado ("Cachorro" é a espécie "Cão" do catálogo). */
    private static final Map<String, String> ESPECIES = Map.of(
            "cachorro", "cao", "caes", "cao", "canino", "cao", "cadela", "cao",
            "felino", "gato", "gata", "gato");

    private TextoNormalizado() {
    }

    public static String comparacao(String texto) {
        if (texto == null) {
            return null;
        }
        String semAcento = Normalizer.normalize(texto.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String s = semAcento.replaceAll("\\s+", " ");
        return s.isEmpty() ? null : s;
    }

    /** Para valores de enum do legado: também trata '_' e '-' como espaço. */
    public static String enumeracao(String texto) {
        String s = comparacao(texto == null ? null : texto.replace('_', ' ').replace('-', ' '));
        return s == null ? null : s.replaceAll("\\s+", " ");
    }

    public static String especie(String texto) {
        String s = comparacao(texto);
        return s == null ? null : ESPECIES.getOrDefault(s, s);
    }

    public static boolean iguais(String a, String b) {
        String x = comparacao(a);
        return x != null && x.equals(comparacao(b));
    }

    public static String limitar(String texto, int maximo) {
        if (texto == null) {
            return null;
        }
        String t = texto.trim();
        return t.length() <= maximo ? t : t.substring(0, maximo).trim();
    }
}
