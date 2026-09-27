package br.com.tecnodreamer.petconnect.migracao.application;

import br.com.tecnodreamer.petconnect.migracao.infrastructure.DestinoMigracao;
import br.com.tecnodreamer.petconnect.pet.domain.Porte;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Pets: {@code vacinado} do legado não migra (é derivado da carteira); foto vem em etapa própria. */
@Component
@Order(120)
public class MigradorPet implements Migrador {

    private final DestinoMigracao destino;

    public MigradorPet(DestinoMigracao destino) {
        this.destino = destino;
    }

    @Override
    public String entidade() {
        return "pet";
    }

    @Override
    public String consulta() {
        return """
                SELECT id, nome, cliente_id, especie_id, raca_id, data_nascimento, peso, cor, castrado, observacoes,
                       ativo, porte
                  FROM pet WHERE id > :ultimo ORDER BY id LIMIT :lote
                """;
    }

    @Override
    public Desfecho migrar(Linha l, Contexto ctx) {
        Optional<Long> cliente = ctx.idNovo("cliente", l.longo("cliente_id"));
        if (cliente.isEmpty()) {
            return Desfecho.ignorado("CLIENTE_NAO_MIGRADO");
        }
        Optional<Long> especie = ctx.idNovo("especie", l.longo("especie_id"));
        if (especie.isEmpty()) {
            return Desfecho.ignorado("ESPECIE_NAO_MIGRADA");
        }
        List<String> pend = new ArrayList<>();
        Long raca = null;
        if (l.longo("raca_id") != null) {
            raca = ctx.idNovo("raca", l.longo("raca_id")).orElse(null);
            if (raca == null) {
                pend.add("RACA_NAO_MIGRADA");
            } else if (!destino.especieDaRaca(raca).orElse(-1L).equals(especie.get())) {
                pend.add("RACA_DE_OUTRA_ESPECIE");
                raca = null;
            }
        }
        BigDecimal peso = l.decimal("peso");
        if (peso != null && (peso.signum() <= 0 || peso.compareTo(new BigDecimal("999.99")) > 0)) {
            pend.add("PESO_INVALIDO");
            peso = null;
        }
        Porte porte = ConversorLegado.porte(l.texto("porte")).anotar(pend);
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("cliente_id", cliente.get());
        v.put("nome", TextoNormalizado.limitar(l.texto("nome"), 50));
        v.put("especie_id", especie.get());
        v.put("raca_id", raca);
        v.put("data_nascimento", l.data("data_nascimento"));
        v.put("peso", peso);
        v.put("cor", TextoNormalizado.limitar(l.texto("cor"), 30));
        v.put("porte", porte == null ? null : porte.name());
        v.put("castrado", ConversorLegado.booleano(l.bruto("castrado"), false).anotar(pend));
        v.put("observacoes", l.texto("observacoes"));
        v.put("ativo", Campos.ativo(l, pend));
        return Desfecho.migrado(destino.inserir("pet", v), pend);
    }
}
