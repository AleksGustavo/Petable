package br.com.tecnodreamer.petconnect.usuario.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * Política única de senha (etapa 03, DA-06): 10–72 caracteres (72 é o limite
 * do BCrypt), maiúscula, minúscula, número e caractere especial, e nunca igual
 * ao username, ao e-mail ou à parte do e-mail antes do "@".
 */
public final class PoliticaSenha {

    public static final int MINIMO = 10;
    public static final int MAXIMO = 72;

    private PoliticaSenha() {
    }

    /** Primeira regra violada, ou vazio se a senha é aceita. */
    public static Optional<String> violacao(String senha, String username, String email) {
        if (senha == null || senha.length() < MINIMO) {
            return Optional.of("A senha deve ter pelo menos " + MINIMO + " caracteres.");
        }
        if (senha.length() > MAXIMO) {
            return Optional.of("A senha deve ter no máximo " + MAXIMO + " caracteres.");
        }
        if (senha.chars().noneMatch(Character::isUpperCase)) {
            return Optional.of("A senha deve ter pelo menos uma letra maiúscula.");
        }
        if (senha.chars().noneMatch(Character::isLowerCase)) {
            return Optional.of("A senha deve ter pelo menos uma letra minúscula.");
        }
        if (senha.chars().noneMatch(Character::isDigit)) {
            return Optional.of("A senha deve ter pelo menos um número.");
        }
        if (senha.chars().allMatch(Character::isLetterOrDigit)) {
            return Optional.of("A senha deve ter pelo menos um caractere especial.");
        }
        String s = senha.toLowerCase(Locale.ROOT);
        if (username != null && s.equals(username.toLowerCase(Locale.ROOT))) {
            return Optional.of("A senha não pode ser igual ao nome de usuário.");
        }
        if (email != null) {
            String e = email.toLowerCase(Locale.ROOT);
            String local = e.contains("@") ? e.substring(0, e.indexOf('@')) : e;
            if (s.equals(e) || s.equals(local)) {
                return Optional.of("A senha não pode ser igual ao e-mail.");
            }
        }
        return Optional.empty();
    }
}
