package br.com.tecnodreamer.petconnect.migracao.application;

import java.util.List;
import java.util.Optional;

/** Valor convertido + código de pendência quando o valor legado não foi reconhecido. */
public record Conversao<T>(T valor, String pendencia) {

    public static <T> Conversao<T> ok(T valor) {
        return new Conversao<>(valor, null);
    }

    public static <T> Conversao<T> comPendencia(T valor, String pendencia) {
        return new Conversao<>(valor, pendencia);
    }

    public Optional<String> pendenciaOpcional() {
        return Optional.ofNullable(pendencia);
    }

    /** Devolve o valor e anota a pendência (se houver) na lista. */
    public T anotar(List<String> pendencias) {
        if (pendencia != null) {
            pendencias.add(pendencia);
        }
        return valor;
    }
}
