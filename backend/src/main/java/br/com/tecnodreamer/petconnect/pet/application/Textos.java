package br.com.tecnodreamer.petconnect.pet.application;

final class Textos {

    private Textos() {
    }

    static String limpo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
