package br.com.tecnodreamer.petconnect.migracao.application;

import br.com.tecnodreamer.petconnect.migracao.infrastructure.DestinoMigracao;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.math.BigDecimal;

@Component
@Order(90)
public class MigradorServico implements Migrador {

    private final DestinoMigracao destino;

    public MigradorServico(DestinoMigracao destino) {
        this.destino = destino;
    }

    @Override
    public String entidade() {
        return "servico";
    }

    @Override
    public String consulta() {
        return "SELECT id, nome, descricao, preco, duracao_media, ativo FROM servico WHERE id > :ultimo ORDER BY id LIMIT :lote";
    }

    @Override
    public Desfecho migrar(Linha l, Contexto ctx) {
        String nome = TextoNormalizado.limitar(l.texto("nome"), 100);
        if (nome == null) {
            return Desfecho.ignorado("NOME_AUSENTE");
        }
        Optional<Long> existente = Campos.casar(destino.servicos(), nome);
        if (existente.isPresent()) {
            return Desfecho.migrado(existente.get());
        }
        BigDecimal preco = l.decimal("preco");
        if (preco == null || preco.signum() < 0) {
            return Desfecho.ignorado("PRECO_INVALIDO");
        }
        List<String> pend = new java.util.ArrayList<>(List.of("CATALOGO_CRIADO"));
        Integer duracao = l.inteiro("duracao_media");
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("nome", nome);
        v.put("descricao", l.texto("descricao"));
        v.put("preco", preco);
        v.put("duracao_minutos", duracao == null || duracao <= 0 ? null : duracao);
        v.put("ativo", Campos.ativo(l, pend));
        return Desfecho.migrado(destino.inserir("servico", v), pend);
    }
}
