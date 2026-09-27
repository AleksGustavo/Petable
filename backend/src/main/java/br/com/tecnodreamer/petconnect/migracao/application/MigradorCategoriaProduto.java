package br.com.tecnodreamer.petconnect.migracao.application;

import br.com.tecnodreamer.petconnect.migracao.infrastructure.DestinoMigracao;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Order(110)
public class MigradorCategoriaProduto implements Migrador {

    private final DestinoMigracao destino;

    public MigradorCategoriaProduto(DestinoMigracao destino) {
        this.destino = destino;
    }

    @Override
    public String entidade() {
        return "categoria_produto";
    }

    @Override
    public String consulta() {
        return "SELECT id, nome, descricao FROM categoria_produto WHERE id > :ultimo ORDER BY id LIMIT :lote";
    }

    @Override
    public Desfecho migrar(Linha l, Contexto ctx) {
        String nome = TextoNormalizado.limitar(l.texto("nome"), 50);
        if (nome == null) {
            return Desfecho.ignorado("NOME_AUSENTE");
        }
        Optional<Long> existente = Campos.casar(destino.categoriasProduto(), nome);
        if (existente.isPresent()) {
            return Desfecho.migrado(existente.get());
        }
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("nome", nome);
        v.put("descricao", TextoNormalizado.limitar(l.texto("descricao"), 255));
        return Desfecho.migrado(destino.inserir("categoria_produto", v), List.of("CATALOGO_CRIADO"));
    }
}
