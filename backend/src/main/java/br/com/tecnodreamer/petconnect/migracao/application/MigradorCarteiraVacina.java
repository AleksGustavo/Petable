package br.com.tecnodreamer.petconnect.migracao.application;

import br.com.tecnodreamer.petconnect.migracao.infrastructure.DestinoMigracao;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Nome livre do legado → vacina do catálogo da espécie do pet; sem correspondência, cria (AJUSTADO). */
@Component
@Order(140)
public class MigradorCarteiraVacina implements Migrador {

    private final DestinoMigracao destino;

    public MigradorCarteiraVacina(DestinoMigracao destino) {
        this.destino = destino;
    }

    @Override
    public String entidade() {
        return "carteira_vacina";
    }

    @Override
    public String consulta() {
        return """
                SELECT id, pet_id, nome_vacina, data_aplicacao, data_proxima, veterinario, observacoes
                  FROM carteira_vacina WHERE id > :ultimo ORDER BY id LIMIT :lote
                """;
    }

    @Override
    public Desfecho migrar(Linha l, Contexto ctx) {
        Optional<Long> pet = ctx.idNovo("pet", l.longo("pet_id"));
        if (pet.isEmpty()) {
            return Desfecho.ignorado("PET_NAO_MIGRADO");
        }
        String nome = TextoNormalizado.limitar(l.texto("nome_vacina"), 100);
        if (nome == null || l.data("data_aplicacao") == null) {
            return Desfecho.ignorado("DADOS_OBRIGATORIOS_AUSENTES");
        }
        Long especie = destino.especieDoPet(pet.get()).orElseThrow();
        List<String> pend = new ArrayList<>();
        Long vacina = Campos.casar(destino.vacinas(especie), nome).orElseGet(() -> {
            pend.add("VACINA_CRIADA");
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("especie_id", especie);
            v.put("nome", nome);
            return destino.inserir("vacina", v);
        });
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("pet_id", pet.get());
        v.put("vacina_id", vacina);
        v.put("data_aplicacao", l.data("data_aplicacao"));
        v.put("data_proxima", l.data("data_proxima"));
        v.put("veterinario", TextoNormalizado.limitar(l.texto("veterinario"), 100));
        v.put("observacoes", l.texto("observacoes"));
        return Desfecho.migrado(destino.inserir("carteira_vacina", v), pend);
    }
}
