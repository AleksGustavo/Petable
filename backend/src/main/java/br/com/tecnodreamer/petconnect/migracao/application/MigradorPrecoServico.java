package br.com.tecnodreamer.petconnect.migracao.application;

import br.com.tecnodreamer.petconnect.migracao.infrastructure.DestinoMigracao;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import br.com.tecnodreamer.petconnect.pet.domain.Porte;
import java.math.BigDecimal;

/** Preço por porte: o preço já cadastrado no sistema novo prevalece (IGNORADO com motivo). */
@Component
@Order(100)
public class MigradorPrecoServico implements Migrador {

    private final DestinoMigracao destino;

    public MigradorPrecoServico(DestinoMigracao destino) {
        this.destino = destino;
    }

    @Override
    public String entidade() {
        return "preco_servico_porte";
    }

    @Override
    public String consulta() {
        return "SELECT id, servico_id, porte, preco FROM preco_servico_porte WHERE id > :ultimo ORDER BY id LIMIT :lote";
    }

    @Override
    public Desfecho migrar(Linha l, Contexto ctx) {
        Optional<Long> servico = ctx.idNovo("servico", l.longo("servico_id"));
        if (servico.isEmpty()) {
            return Desfecho.ignorado("SERVICO_NAO_MIGRADO");
        }
        Conversao<Porte> porte = ConversorLegado.porte(l.texto("porte"));
        if (porte.valor() == null || porte.pendencia() != null) {
            return Desfecho.ignorado("PORTE_DESCONHECIDO");
        }
        BigDecimal preco = l.decimal("preco");
        if (preco == null || preco.signum() < 0) {
            return Desfecho.ignorado("PRECO_INVALIDO");
        }
        if (destino.precoPorteExiste(servico.get(), porte.valor().name())) {
            return Desfecho.ignorado("PRECO_PORTE_JA_EXISTE");
        }
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("servico_id", servico.get());
        v.put("porte", porte.valor().name());
        v.put("preco", preco);
        return Desfecho.migrado(destino.inserir("preco_servico_porte", v));
    }
}
