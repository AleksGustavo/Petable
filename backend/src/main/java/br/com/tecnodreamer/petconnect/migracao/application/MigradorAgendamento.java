package br.com.tecnodreamer.petconnect.migracao.application;

import br.com.tecnodreamer.petconnect.migracao.infrastructure.DestinoMigracao;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Order(190)
public class MigradorAgendamento implements Migrador {

    private final DestinoMigracao destino;

    public MigradorAgendamento(DestinoMigracao destino) {
        this.destino = destino;
    }

    @Override
    public String entidade() {
        return "agendamento";
    }

    @Override
    public String consulta() {
        return """
                SELECT id, pet_id, servico_id, funcionario_id, data_agendamento, status, observacoes, total_estimado
                  FROM agendamento WHERE id > :ultimo ORDER BY id LIMIT :lote
                """;
    }

    @Override
    public Desfecho migrar(Linha l, Contexto ctx) {
        Optional<Long> pet = ctx.idNovo("pet", l.longo("pet_id"));
        if (pet.isEmpty()) {
            return Desfecho.ignorado("PET_NAO_MIGRADO");
        }
        Optional<Long> servico = ctx.idNovo("servico", l.longo("servico_id"));
        if (servico.isEmpty()) {
            return Desfecho.ignorado("SERVICO_NAO_MIGRADO");
        }
        if (l.dataHora("data_agendamento") == null) {
            return Desfecho.ignorado("DADOS_OBRIGATORIOS_AUSENTES");
        }
        List<String> pend = new ArrayList<>();
        BigDecimal total = l.decimal("total_estimado");
        if (total == null || total.signum() < 0) {
            pend.add("TOTAL_ESTIMADO_INVALIDO");
            total = BigDecimal.ZERO.setScale(2);
        }
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("pet_id", pet.get());
        v.put("servico_id", servico.get());
        v.put("funcionario_id", MigradorCompra.opcional(ctx, "funcionario", l.longo("funcionario_id"), pend,
                "FUNCIONARIO_NAO_MIGRADO"));
        v.put("data_hora", l.dataHora("data_agendamento"));
        v.put("status", ConversorLegado.statusAgendamento(l.texto("status")).anotar(pend).name());
        v.put("total_estimado", total);
        v.put("observacoes", l.texto("observacoes"));
        return Desfecho.migrado(destino.inserir("agendamento", v), pend);
    }
}
