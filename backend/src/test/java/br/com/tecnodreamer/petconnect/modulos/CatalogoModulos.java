package br.com.tecnodreamer.petconnect.modulos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.AntPathMatcher;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Leitor do catálogo de módulos ({@code modulos/catalogo.json}) para os testes de rastreabilidade. */
final class CatalogoModulos {

    /** Quem é dono de uma rota: o núcleo, um módulo, uma integração ou a infraestrutura. */
    record Dono(String tipo, String id) {
        @Override
        public String toString() {
            return tipo + ":" + id;
        }
    }

    /** Uma entrada do catálogo: métodos (vazio = qualquer) e um padrão de caminho no estilo Ant. */
    record Entrada(Set<String> metodos, String padrao, Dono dono) {
        boolean aceita(Set<String> metodosDaRota, String caminho, AntPathMatcher m) {
            boolean metodoOk = metodos.isEmpty() || metodosDaRota.isEmpty() || metodosDaRota.stream().anyMatch(metodos::contains);
            return metodoOk && m.match(padrao, caminho);
        }
    }

    final JsonNode raiz;

    CatalogoModulos() {
        try (InputStream in = new ClassPathResource("modulos/catalogo.json").getInputStream()) {
            this.raiz = new ObjectMapper().readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException("catalogo.json ilegível", e);
        }
    }

    List<String> idsDosModulos() {
        List<String> ids = new ArrayList<>();
        raiz.get("modulos").forEach(m -> ids.add(m.get("id").asText()));
        return ids;
    }

    JsonNode modulo(String id) {
        for (JsonNode m : raiz.get("modulos")) {
            if (m.get("id").asText().equals(id)) {
                return m;
            }
        }
        throw new IllegalArgumentException("módulo inexistente: " + id);
    }

    static List<String> textos(JsonNode no, String campo) {
        List<String> r = new ArrayList<>();
        if (no != null && no.has(campo)) {
            no.get(campo).forEach(x -> r.add(x.asText()));
        }
        return r;
    }

    /** Todas as entradas de rota da API, de todos os donos. */
    List<Entrada> entradasDeRotas() {
        List<Entrada> r = new ArrayList<>();
        adicionar(r, raiz.get("infra"), new Dono("infra", "infra"));
        adicionar(r, raiz.get("nucleo"), new Dono("nucleo", "nucleo"));
        for (JsonNode m : raiz.get("modulos")) {
            adicionar(r, m, new Dono("modulo", m.get("id").asText()));
        }
        for (JsonNode i : raiz.get("integracoes")) {
            adicionar(r, i, new Dono("integracao", i.get("id").asText()));
        }
        return r;
    }

    private static void adicionar(List<Entrada> destino, JsonNode no, Dono dono) {
        for (String bruto : textos(no, "rotas")) {
            String texto = bruto.trim();
            Set<String> metodos = Set.of();
            String padrao = texto;
            if (!texto.startsWith("/")) {
                int espaco = texto.indexOf(' ');
                metodos = Set.of(texto.substring(0, espaco).split(","));
                padrao = texto.substring(espaco + 1).trim();
            }
            destino.add(new Entrada(metodos, padrao, dono));
        }
    }

    /** Dono da rota: a entrada mais específica; empate entre donos diferentes é erro (devolve null e a lista). */
    Map<String, Object> resolver(Set<String> metodosDaRota, String caminho, List<Entrada> entradas) {
        AntPathMatcher m = new AntPathMatcher();
        List<Entrada> candidatas = new ArrayList<>();
        for (Entrada e : entradas) {
            if (e.aceita(metodosDaRota, caminho, m)) {
                candidatas.add(e);
            }
        }
        Map<String, Object> r = new LinkedHashMap<>();
        if (candidatas.isEmpty()) {
            r.put("donos", Set.of());
            return r;
        }
        Comparator<Entrada> especificidade = (a, b) -> m.getPatternComparator(caminho).compare(a.padrao(), b.padrao());
        candidatas.sort(especificidade);
        Entrada melhor = candidatas.get(0);
        Set<Dono> empatados = new TreeSet<>(Comparator.comparing(Dono::toString));
        for (Entrada e : candidatas) {
            // Mesma especificidade que a melhor, ou padrão idêntico: pertence ao empate.
            if (especificidade.compare(e, melhor) == 0 || e.padrao().equals(melhor.padrao())) {
                empatados.add(e.dono());
            }
        }
        r.put("donos", empatados);
        r.put("padrao", melhor.padrao());
        return r;
    }
}
