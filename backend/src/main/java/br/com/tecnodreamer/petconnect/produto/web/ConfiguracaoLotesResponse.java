package br.com.tecnodreamer.petconnect.produto.web;

import java.util.List;

/** O que a tela precisa saber da instalação: se o controle de lote está liberado e as faixas dos alertas de vencimento. */
public record ConfiguracaoLotesResponse(boolean lotesHabilitado, List<Integer> faixasAlertaDias) {
}
