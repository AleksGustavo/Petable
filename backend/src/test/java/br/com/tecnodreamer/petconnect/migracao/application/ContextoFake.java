package br.com.tecnodreamer.petconnect.migracao.application;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Contexto em memória para os testes unitários dos migradores. */
class ContextoFake implements Contexto {

    static final Long TECNICO = 99L;

    private final Map<String, Long> ids = new HashMap<>();
    private final List<Linha> legado = new ArrayList<>();
    private OpcoesMigracao opcoes = new OpcoesMigracao(true, 500, Path.of("build/tmp"), null, false, null);

    ContextoFake mapear(String entidade, long idLegado, long idNovo) {
        ids.put(entidade + ":" + idLegado, idNovo);
        return this;
    }

    ContextoFake legado(Linha... linhas) {
        legado.addAll(List.of(linhas));
        return this;
    }

    ContextoFake viaCep(boolean ligado) {
        opcoes = new OpcoesMigracao(true, 500, Path.of("build/tmp"), null, ligado, null);
        return this;
    }

    @Override
    public Optional<Long> idNovo(String entidade, Long idLegado) {
        return Optional.ofNullable(ids.get(entidade + ":" + idLegado));
    }

    @Override
    public List<Linha> consultarLegado(String sql, Map<String, ?> parametros) {
        return legado;
    }

    @Override
    public OpcoesMigracao opcoes() {
        return opcoes;
    }

    @Override
    public Long usuarioTecnicoId() {
        return TECNICO;
    }
}
