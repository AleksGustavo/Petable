package br.com.tecnodreamer.petconnect.shared.logging;

import java.util.regex.Pattern;

/**
 * Rede de segurança dos logs (DA-10): mascara CPF, e-mail, telefone, JWT e token Bearer
 * em qualquer texto. O código já evita logar dado pessoal; isto pega o que escapar
 * (mensagens de exceção do banco, por exemplo).
 */
public final class MascaraDadosPessoais {

    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})");
    private static final Pattern CPF = Pattern.compile("(?<!\\d)\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}(?!\\d)");
    private static final Pattern TELEFONE = Pattern.compile("\\(?(?<!\\d)\\d{2}\\)?\\s?9?\\d{4}-?\\d{4}(?!\\d)");

    private MascaraDadosPessoais() {
    }

    public static String mascarar(String texto) {
        if (texto == null || texto.isEmpty()) {
            return texto;
        }
        String s = JWT.matcher(texto).replaceAll("***jwt***");
        s = BEARER.matcher(s).replaceAll("Bearer ***");
        s = EMAIL.matcher(s).replaceAll("***@$1");
        s = CPF.matcher(s).replaceAll("***.***.***-**");
        return TELEFONE.matcher(s).replaceAll("(**) *****-****");
    }
}
