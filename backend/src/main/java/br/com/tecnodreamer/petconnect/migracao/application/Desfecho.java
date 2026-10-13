package br.com.tecnodreamer.petconnect.migracao.application;

import java.util.List;

/**
 * Resultado de um registro legado. Registro gravado com alguma pendência é AJUSTADO;
 * ignorado nunca tem id novo. Pendências são CÓDIGOS (sem dado pessoal).
 */
public record Desfecho(Situacao situacao, Long idNovo, List<String> pendencias) {

    public Desfecho {
        pendencias = List.copyOf(pendencias);
    }

    public static Desfecho migrado(Long idNovo, List<String> pendencias) {
        return new Desfecho(pendencias.isEmpty() ? Situacao.MIGRADO : Situacao.AJUSTADO, idNovo, pendencias);
    }

    public static Desfecho migrado(Long idNovo) {
        return migrado(idNovo, List.of());
    }

    public static Desfecho ignorado(String motivo) {
        return new Desfecho(Situacao.IGNORADO, null, List.of(motivo));
    }
}
