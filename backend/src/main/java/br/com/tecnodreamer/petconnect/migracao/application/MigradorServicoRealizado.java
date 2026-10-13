package br.com.tecnodreamer.petconnect.migracao.application;

import br.com.tecnodreamer.petconnect.migracao.infrastructure.DestinoMigracao;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Serviço realizado; o lançamento financeiro vem depois, pelo backfill (DA-29). */
@Component
@Order(200)
public class MigradorServicoRealizado implements Migrador {

    private final DestinoMigracao destino;

    public MigradorServicoRealizado(DestinoMigracao destino) {
        this.destino = destino;
    }

    @Override
    public String entidade() {
        return "servico_realizado";
    }

    @Override
    public String consulta() {
        return """
                SELECT id, agendamento_id, pet_id, servico_id, funcionario_id, data_servico, valor, observacoes,
                       created_at
                  FROM servico_realizado WHERE id > :ultimo ORDER BY id LIMIT :lote
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
        BigDecimal valor = l.decimal("valor");
        if (valor == null || valor.signum() < 0) {
            return Desfecho.ignorado("VALOR_INVALIDO");
        }
        List<String> pend = new ArrayList<>();
        Long agendamento = MigradorCompra.opcional(ctx, "agendamento", l.longo("agendamento_id"), pend,
                "AGENDAMENTO_NAO_MIGRADO");
        if (agendamento != null && destino.realizadoDoAgendamentoExiste(agendamento)) {
            pend.add("AGENDAMENTO_JA_TEM_SERVICO");
            agendamento = null;
        }
        LocalDateTime data = l.dataHora("data_servico");
        if (data == null) {
            pend.add("DATA_SERVICO_AUSENTE");
            data = l.dataHora("created_at");
        }
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("agendamento_id", agendamento);
        v.put("pet_id", pet.get());
        v.put("servico_id", servico.get());
        v.put("funcionario_id", MigradorCompra.opcional(ctx, "funcionario", l.longo("funcionario_id"), pend,
                "FUNCIONARIO_NAO_MIGRADO"));
        v.put("data_servico", data);
        v.put("valor", valor);
        v.put("observacoes", l.texto("observacoes"));
        return Desfecho.migrado(destino.inserir("servico_realizado", v), pend);
    }
}
