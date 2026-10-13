package br.com.tecnodreamer.petconnect.migracao.application;

import br.com.tecnodreamer.petconnect.migracao.infrastructure.DestinoMigracao;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Order(80)
public class MigradorVacina implements Migrador {

    private final DestinoMigracao destino;

    public MigradorVacina(DestinoMigracao destino) {
        this.destino = destino;
    }

    @Override
    public String entidade() {
        return "vacina";
    }

    @Override
    public String consulta() {
        return """
                SELECT id, nome, especie_id, doenca_protecao, validade_padrao_meses, preco, observacoes, ativo
                  FROM vacina WHERE id > :ultimo ORDER BY id LIMIT :lote
                """;
    }

    @Override
    public Desfecho migrar(Linha l, Contexto ctx) {
        Optional<Long> especie = ctx.idNovo("especie", l.longo("especie_id"));
        if (especie.isEmpty()) {
            return Desfecho.ignorado("ESPECIE_NAO_MIGRADA");
        }
        String nome = TextoNormalizado.limitar(l.texto("nome"), 100);
        if (nome == null) {
            return Desfecho.ignorado("NOME_AUSENTE");
        }
        Optional<Long> existente = Campos.casar(destino.vacinas(especie.get()), nome);
        if (existente.isPresent()) {
            return Desfecho.migrado(existente.get());
        }
        List<String> pend = new java.util.ArrayList<>(List.of("CATALOGO_CRIADO"));
        Integer meses = l.inteiro("validade_padrao_meses");
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("especie_id", especie.get());
        v.put("nome", nome);
        v.put("doenca_protecao", TextoNormalizado.limitar(l.texto("doenca_protecao"), 150));
        v.put("validade_padrao_meses", meses == null || meses <= 0 ? 12 : meses);
        v.put("preco", l.decimal("preco"));
        v.put("observacoes", l.texto("observacoes"));
        v.put("ativo", Campos.ativo(l, pend));
        return Desfecho.migrado(destino.inserir("vacina", v), pend);
    }
}
