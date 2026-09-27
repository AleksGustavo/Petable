package br.com.tecnodreamer.petconnect.migracao.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** O que um migrador enxerga da execução corrente. */
public interface Contexto {

    /** Id novo de um registro legado já migrado (nesta ou em execução anterior). */
    Optional<Long> idNovo(String entidade, Long idLegado);

    /** Consulta somente leitura ao legado (itens de venda/compra). */
    List<Linha> consultarLegado(String sql, Map<String, ?> parametros);

    OpcoesMigracao opcoes();

    /** Usuário técnico inativo "migracao.legado", autor dos ajustes de estoque e das vendas sem operador. */
    Long usuarioTecnicoId();
}
