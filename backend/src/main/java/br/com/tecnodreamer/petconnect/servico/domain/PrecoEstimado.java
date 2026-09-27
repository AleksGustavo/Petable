package br.com.tecnodreamer.petconnect.servico.domain;

import java.math.BigDecimal;

public record PrecoEstimado(OrigemPreco origem, BigDecimal valor) {
}
