package br.com.tecnodreamer.petconnect.migracao.application;

import br.com.tecnodreamer.petconnect.migracao.infrastructure.DestinoMigracao;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Papéis não são criados: o legado é casado com ADMIN/VENDAS/SERVICO do seed. */
@Component
@Order(10)
public class MigradorPapel implements Migrador {

    private final DestinoMigracao destino;

    public MigradorPapel(DestinoMigracao destino) {
        this.destino = destino;
    }

    @Override
    public String entidade() {
        return "papel";
    }

    @Override
    public String consulta() {
        return "SELECT id, nome FROM papel WHERE id > :ultimo ORDER BY id LIMIT :lote";
    }

    @Override
    public Desfecho migrar(Linha l, Contexto ctx) {
        List<String> pend = new ArrayList<>();
        String nome = ConversorLegado.papel(l.texto("nome")).anotar(pend);
        return Desfecho.migrado(destino.papelId(nome).orElseThrow(), pend);
    }
}
