package br.com.tecnodreamer.petconnect.migracao.infrastructure;

import br.com.tecnodreamer.petconnect.migracao.application.OpcoesMigracao;
import br.com.tecnodreamer.petconnect.venda.domain.FormaPagamento;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/** petconnect.migracao.* (§11). Nada aqui liga a migração sozinho: {@code legado} é false por padrão. */
@ConfigurationProperties("petconnect.migracao")
public record MigracaoProperties(boolean legado, Boolean dryRun, Integer lote, String relatorioDir, String fotosDir,
        Viacep viacep, FormaPagamento backfillServicoForma, Boolean encerrarAoFinal) {

    public record Viacep(boolean enabled, Integer timeoutMs) {
    }

    public OpcoesMigracao opcoes() {
        return new OpcoesMigracao(
                dryRun == null || dryRun,
                lote == null ? OpcoesMigracao.LOTE_PADRAO : lote,
                Path.of(relatorioDir == null || relatorioDir.isBlank() ? "build/migracao-relatorios" : relatorioDir),
                fotosDir == null || fotosDir.isBlank() ? null : Path.of(fotosDir),
                viacep != null && viacep.enabled(),
                backfillServicoForma);
    }

    public boolean encerrar() {
        return encerrarAoFinal == null || encerrarAoFinal;
    }
}
