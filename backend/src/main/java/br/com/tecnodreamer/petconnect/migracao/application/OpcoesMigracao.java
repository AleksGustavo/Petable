package br.com.tecnodreamer.petconnect.migracao.application;

import br.com.tecnodreamer.petconnect.venda.domain.FormaPagamento;

import java.nio.file.Path;

/**
 * Parâmetros de uma execução. {@code fotosDir} nulo = fotos não importadas;
 * {@code formaServicoBackfill} nula = serviços antigos ficam sem lançamento (pendência).
 */
public record OpcoesMigracao(boolean dryRun, int lote, Path relatorioDir, Path fotosDir, boolean viaCep,
        FormaPagamento formaServicoBackfill) {

    public static final int LOTE_PADRAO = 500;

    public OpcoesMigracao {
        if (lote < 1 || lote > 5000) {
            throw new IllegalArgumentException("petconnect.migracao.lote deve estar entre 1 e 5000.");
        }
    }
}
