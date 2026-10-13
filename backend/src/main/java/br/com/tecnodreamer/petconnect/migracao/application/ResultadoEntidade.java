package br.com.tecnodreamer.petconnect.migracao.application;

/** Contagens de uma entidade numa execução. lidos = migrados + ajustados + ignorados + jaMigrados. */
public final class ResultadoEntidade {

    private final String entidade;
    private long lidos;
    private long migrados;
    private long ajustados;
    private long ignorados;
    private long jaMigrados;

    public ResultadoEntidade(String entidade) {
        this.entidade = entidade;
    }

    void contar(Situacao situacao) {
        lidos++;
        switch (situacao) {
            case MIGRADO -> migrados++;
            case AJUSTADO -> ajustados++;
            case IGNORADO -> ignorados++;
        }
    }

    void contarJaMigrado() {
        lidos++;
        jaMigrados++;
    }

    public boolean fecha() {
        return lidos == migrados + ajustados + ignorados + jaMigrados;
    }

    public String entidade() {
        return entidade;
    }

    public long lidos() {
        return lidos;
    }

    public long migrados() {
        return migrados;
    }

    public long ajustados() {
        return ajustados;
    }

    public long ignorados() {
        return ignorados;
    }

    public long jaMigrados() {
        return jaMigrados;
    }
}
