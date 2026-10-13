package br.com.tecnodreamer.petconnect.financeiro.web;

/** Origem legível do lançamento, derivada de chave_origem ("VENDA:91" → VENDA/91; "MANUAL:uuid" → MANUAL/null). */
public record OrigemResponse(String tipo, Long id) {

    public static OrigemResponse de(String chave) {
        if (chave == null) {
            return null;
        }
        int i = chave.indexOf(':');
        String tipo = i < 0 ? chave : chave.substring(0, i);
        String resto = i < 0 ? "" : chave.substring(i + 1);
        Long id = resto.chars().allMatch(Character::isDigit) && !resto.isEmpty() ? Long.valueOf(resto) : null;
        return new OrigemResponse(tipo, id);
    }
}
