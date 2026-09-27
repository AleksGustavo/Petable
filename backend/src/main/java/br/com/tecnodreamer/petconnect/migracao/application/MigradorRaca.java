package br.com.tecnodreamer.petconnect.migracao.application;

import br.com.tecnodreamer.petconnect.migracao.infrastructure.DestinoMigracao;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Order(70)
public class MigradorRaca implements Migrador {

    private final DestinoMigracao destino;

    public MigradorRaca(DestinoMigracao destino) {
        this.destino = destino;
    }

    @Override
    public String entidade() {
        return "raca";
    }

    @Override
    public String consulta() {
        return "SELECT id, nome, especie_id FROM raca WHERE id > :ultimo ORDER BY id LIMIT :lote";
    }

    @Override
    public Desfecho migrar(Linha l, Contexto ctx) {
        Optional<Long> especie = ctx.idNovo("especie", l.longo("especie_id"));
        if (especie.isEmpty()) {
            return Desfecho.ignorado("ESPECIE_NAO_MIGRADA");
        }
        String nome = TextoNormalizado.limitar(l.texto("nome"), 50);
        if (nome == null) {
            return Desfecho.ignorado("NOME_AUSENTE");
        }
        Optional<Long> existente = Campos.casar(destino.racas(especie.get()), nome);
        if (existente.isPresent()) {
            return Desfecho.migrado(existente.get());
        }
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("especie_id", especie.get());
        v.put("nome", nome);
        return Desfecho.migrado(destino.inserir("raca", v), List.of("CATALOGO_CRIADO"));
    }
}
