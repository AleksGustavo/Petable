package br.com.tecnodreamer.petconnect.migracao.application;

import br.com.tecnodreamer.petconnect.migracao.infrastructure.DestinoMigracao;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Casa pelo nome normalizado (com sinônimos: "Cachorro" = "Cão"); ausente é criada (AJUSTADO). */
@Component
@Order(60)
public class MigradorEspecie implements Migrador {

    private final DestinoMigracao destino;

    public MigradorEspecie(DestinoMigracao destino) {
        this.destino = destino;
    }

    @Override
    public String entidade() {
        return "especie";
    }

    @Override
    public String consulta() {
        return "SELECT id, nome FROM especie WHERE id > :ultimo ORDER BY id LIMIT :lote";
    }

    @Override
    public Desfecho migrar(Linha l, Contexto ctx) {
        String nome = TextoNormalizado.limitar(l.texto("nome"), 50);
        if (nome == null) {
            return Desfecho.ignorado("NOME_AUSENTE");
        }
        String chave = TextoNormalizado.especie(nome);
        Optional<Long> existente = destino.especies().stream()
                .filter(e -> chave.equals(TextoNormalizado.especie(e.nome()))).map(DestinoMigracao.Nomeado::id).findFirst();
        if (existente.isPresent()) {
            return Desfecho.migrado(existente.get());
        }
        return Desfecho.migrado(destino.inserir("especie", Map.of("nome", nome)), List.of("CATALOGO_CRIADO"));
    }
}
