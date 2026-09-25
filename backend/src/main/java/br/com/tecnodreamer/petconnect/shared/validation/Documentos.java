package br.com.tecnodreamer.petconnect.shared.validation;

/** Normalização e validação de documentos brasileiros (guardados só com dígitos). */
public final class Documentos {

    private Documentos() {
    }

    /** Remove tudo que não for dígito; {@code null} ou vazio viram {@code null}. */
    public static String somenteDigitos(String valor) {
        if (valor == null) {
            return null;
        }
        String digitos = valor.replaceAll("\\D", "");
        return digitos.isEmpty() ? null : digitos;
    }

    public static boolean cpfValido(String valor) {
        String cpf = somenteDigitos(valor);
        if (cpf == null || cpf.length() != 11 || cpf.chars().distinct().count() == 1) {
            return false;
        }
        return digitoCpf(cpf, 9) == cpf.charAt(9) - '0' && digitoCpf(cpf, 10) == cpf.charAt(10) - '0';
    }

    private static int digitoCpf(String cpf, int tamanho) {
        int soma = 0;
        for (int i = 0; i < tamanho; i++) {
            soma += (cpf.charAt(i) - '0') * (tamanho + 1 - i);
        }
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }

    /** Fixo (10 dígitos) ou celular (11), sempre com DDD. */
    public static boolean telefoneValido(String valor) {
        String tel = somenteDigitos(valor);
        return tel != null && (tel.length() == 10 || tel.length() == 11);
    }

    public static boolean cepValido(String valor) {
        String cep = somenteDigitos(valor);
        return cep != null && cep.length() == 8;
    }
}
