package br.com.tecnodreamer.petconnect.migracao.application;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;

/**
 * Relatório Markdown da execução (§10). Só contagens, IDs LEGADOS e códigos: nunca
 * nome, documento, e-mail, telefone, endereço, senha, hash ou token (R-34).
 */
@Service
public class MigracaoRelatorioService {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public Path escrever(ResultadoMigracao r, OpcoesMigracao o) {
        String nome = "migracao-legado-" + r.execucaoId() + (r.dryRun() ? "-dry-run" : "-real") + ".md";
        Path destino = o.relatorioDir().resolve(nome);
        try {
            Files.createDirectories(o.relatorioDir());
            Files.writeString(destino, conteudo(r, o), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return destino;
    }

    public String conteudo(ResultadoMigracao r, OpcoesMigracao o) {
        StringBuilder s = new StringBuilder();
        s.append("# Relatório — Migração legado\n\n");
        s.append("- execução: #").append(r.execucaoId()).append('\n');
        s.append("- modo: ").append(r.dryRun() ? "DRY_RUN (nada foi gravado)" : "REAL").append('\n');
        s.append("- status: ").append(r.status()).append('\n');
        s.append("- início: ").append(r.inicio().format(DATA)).append('\n');
        if (r.fim() != null) {
            s.append("- fim: ").append(r.fim().format(DATA)).append('\n');
        }
        if (r.mensagem() != null) {
            s.append("- mensagem: ").append(r.mensagem()).append('\n');
        }
        s.append("- lote: ").append(o.lote()).append('\n');
        s.append("- ViaCEP: ").append(o.viaCep() ? "habilitado" : "desabilitado").append('\n');
        s.append("- fotos: ").append(o.fotosDir() == null ? "não importadas (diretório não configurado)" : "importadas")
                .append('\n');
        s.append("- forma de pagamento dos serviços antigos: ")
                .append(o.formaServicoBackfill() == null ? "não configurada" : o.formaServicoBackfill()).append("\n\n");

        s.append("| Entidade | Lidos | Migrados | Ajustados | Ignorados | Já migrados |\n");
        s.append("|---|---:|---:|---:|---:|---:|\n");
        for (ResultadoEntidade e : r.entidades()) {
            s.append("| ").append(e.entidade()).append(" | ").append(e.lidos()).append(" | ").append(e.migrados())
                    .append(" | ").append(e.ajustados()).append(" | ").append(e.ignorados()).append(" | ")
                    .append(e.jaMigrados()).append(" |\n");
        }
        s.append("| **total** | ").append(r.total(ResultadoEntidade::lidos)).append(" | ")
                .append(r.total(ResultadoEntidade::migrados)).append(" | ").append(r.total(ResultadoEntidade::ajustados))
                .append(" | ").append(r.total(ResultadoEntidade::ignorados)).append(" | ")
                .append(r.total(ResultadoEntidade::jaMigrados)).append(" |\n\n");

        if (r.backfill() != null) {
            s.append("## Backfill financeiro\n");
            s.append("- vendas: ").append(r.backfill().vendas()).append('\n');
            s.append("- estornos: ").append(r.backfill().estornos()).append('\n');
            s.append("- contas a pagar: ").append(r.backfill().contas()).append('\n');
            s.append("- serviços: ").append(r.backfill().servicos()).append('\n');
            s.append("- serviços pendentes (sem forma de pagamento): ").append(r.backfill().servicosPendentes())
                    .append("\n\n");
        }

        s.append("## Pendências (").append(r.pendencias().size()).append(")\n");
        if (r.pendencias().isEmpty()) {
            s.append("- nenhuma\n");
        }
        for (Pendencia p : r.pendencias()) {
            s.append("- ").append(p.entidade().toUpperCase(java.util.Locale.ROOT));
            if (p.idLegado() != null) {
                s.append(" legado #").append(p.idLegado());
            }
            s.append(" — ").append(p.codigo()).append('\n');
        }

        s.append("\n## Conferências\n");
        if (r.conferencias().isEmpty()) {
            s.append("- não executadas (a execução parou antes)\n");
        }
        for (Conferencia c : r.conferencias()) {
            s.append("- ").append(c.nome()).append(": ").append(c.ok() ? "PASS" : "FAIL").append(" (")
                    .append(c.detalhe()).append(")\n");
        }
        return s.toString();
    }
}
