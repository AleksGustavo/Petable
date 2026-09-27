package br.com.tecnodreamer.petconnect.migracao.application;

import br.com.tecnodreamer.petconnect.financeiro.application.FinanceiroBackfill;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tudo o que vai para o relatório e para a auditoria da execução. */
public final class ResultadoMigracao {

    public enum Status { EM_ANDAMENTO, CONCLUIDA, FALHOU }

    private final Long execucaoId;
    private final boolean dryRun;
    private final LocalDateTime inicio;
    private LocalDateTime fim;
    private Status status = Status.EM_ANDAMENTO;
    private String mensagem;
    private final Map<String, ResultadoEntidade> entidades = new LinkedHashMap<>();
    private final List<Pendencia> pendencias = new ArrayList<>();
    private final List<Conferencia> conferencias = new ArrayList<>();
    private FinanceiroBackfill.Resultado backfill;
    private Path relatorio;

    public ResultadoMigracao(Long execucaoId, boolean dryRun, LocalDateTime inicio) {
        this.execucaoId = execucaoId;
        this.dryRun = dryRun;
        this.inicio = inicio;
    }

    ResultadoEntidade entidade(String nome) {
        return entidades.computeIfAbsent(nome, ResultadoEntidade::new);
    }

    void pendencia(Pendencia p) {
        pendencias.add(p);
    }

    void conferencias(List<Conferencia> lista) {
        conferencias.addAll(lista);
    }

    void backfill(FinanceiroBackfill.Resultado r) {
        this.backfill = r;
    }

    void finalizar(Status novo, String msg, LocalDateTime quando) {
        this.status = novo;
        this.mensagem = msg;
        this.fim = quando;
    }

    void relatorio(Path caminho) {
        this.relatorio = caminho;
    }

    public long total(java.util.function.ToLongFunction<ResultadoEntidade> campo) {
        return entidades.values().stream().mapToLong(campo).sum();
    }

    public boolean conferenciasOk() {
        return conferencias.stream().allMatch(Conferencia::ok);
    }

    public Long execucaoId() {
        return execucaoId;
    }

    public boolean dryRun() {
        return dryRun;
    }

    public LocalDateTime inicio() {
        return inicio;
    }

    public LocalDateTime fim() {
        return fim;
    }

    public Status status() {
        return status;
    }

    public String mensagem() {
        return mensagem;
    }

    public List<ResultadoEntidade> entidades() {
        return List.copyOf(entidades.values());
    }

    public ResultadoEntidade daEntidade(String nome) {
        return entidades.getOrDefault(nome, new ResultadoEntidade(nome));
    }

    public List<Pendencia> pendencias() {
        return List.copyOf(pendencias);
    }

    public List<Conferencia> conferencias() {
        return List.copyOf(conferencias);
    }

    public FinanceiroBackfill.Resultado backfill() {
        return backfill;
    }

    public Path relatorio() {
        return relatorio;
    }
}
