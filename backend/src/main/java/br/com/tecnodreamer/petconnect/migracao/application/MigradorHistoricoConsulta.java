package br.com.tecnodreamer.petconnect.migracao.application;

import br.com.tecnodreamer.petconnect.migracao.infrastructure.DestinoMigracao;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
@Order(150)
public class MigradorHistoricoConsulta implements Migrador {

    private final DestinoMigracao destino;

    public MigradorHistoricoConsulta(DestinoMigracao destino) {
        this.destino = destino;
    }

    @Override
    public String entidade() {
        return "historico_consulta";
    }

    @Override
    public String consulta() {
        return """
                SELECT id, pet_id, data_consulta, veterinario, motivo, diagnostico, tratamento, observacoes
                  FROM historico_consulta WHERE id > :ultimo ORDER BY id LIMIT :lote
                """;
    }

    @Override
    public Desfecho migrar(Linha l, Contexto ctx) {
        Optional<Long> pet = ctx.idNovo("pet", l.longo("pet_id"));
        if (pet.isEmpty()) {
            return Desfecho.ignorado("PET_NAO_MIGRADO");
        }
        String veterinario = TextoNormalizado.limitar(l.texto("veterinario"), 100);
        if (veterinario == null || l.dataHora("data_consulta") == null) {
            return Desfecho.ignorado("DADOS_OBRIGATORIOS_AUSENTES");
        }
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("pet_id", pet.get());
        v.put("data_consulta", l.dataHora("data_consulta"));
        v.put("veterinario", veterinario);
        v.put("motivo", l.texto("motivo"));
        v.put("diagnostico", l.texto("diagnostico"));
        v.put("tratamento", l.texto("tratamento"));
        v.put("observacoes", l.texto("observacoes"));
        return Desfecho.migrado(destino.inserir("historico_consulta", v));
    }
}
