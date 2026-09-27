package br.com.tecnodreamer.petconnect.migracao.application;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** O legado guarda "estado" livre (sigla ou nome). */
final class Ufs {

    private static final Set<String> SIGLAS = Set.of("AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS",
            "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO");

    private static final Map<String, String> NOMES = Map.ofEntries(
            Map.entry("acre", "AC"), Map.entry("alagoas", "AL"), Map.entry("amapa", "AP"), Map.entry("amazonas", "AM"),
            Map.entry("bahia", "BA"), Map.entry("ceara", "CE"), Map.entry("distrito federal", "DF"),
            Map.entry("espirito santo", "ES"), Map.entry("goias", "GO"), Map.entry("maranhao", "MA"),
            Map.entry("mato grosso", "MT"), Map.entry("mato grosso do sul", "MS"), Map.entry("minas gerais", "MG"),
            Map.entry("para", "PA"), Map.entry("paraiba", "PB"), Map.entry("parana", "PR"), Map.entry("pernambuco", "PE"),
            Map.entry("piaui", "PI"), Map.entry("rio de janeiro", "RJ"), Map.entry("rio grande do norte", "RN"),
            Map.entry("rio grande do sul", "RS"), Map.entry("rondonia", "RO"), Map.entry("roraima", "RR"),
            Map.entry("santa catarina", "SC"), Map.entry("sao paulo", "SP"), Map.entry("sergipe", "SE"),
            Map.entry("tocantins", "TO"));

    private Ufs() {
    }

    static Optional<String> sigla(String estado) {
        String s = TextoNormalizado.comparacao(estado);
        if (s == null) {
            return Optional.empty();
        }
        String upper = s.toUpperCase(Locale.ROOT);
        if (SIGLAS.contains(upper)) {
            return Optional.of(upper);
        }
        return Optional.ofNullable(NOMES.get(s));
    }
}
