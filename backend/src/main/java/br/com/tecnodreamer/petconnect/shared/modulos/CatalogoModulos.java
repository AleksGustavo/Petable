package br.com.tecnodreamer.petconnect.shared.modulos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.AntPathMatcher;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Catálogo de módulos ({@code modulos/catalogo.json}, etapa 06) em memória: quem é dono de cada rota da API, as dependências, as
 * integrações, as regras de relatório e os bloqueios de remoção. Imutável depois de carregado. A decisão de acesso fica em
 * {@link ModulosAtivos}; esta classe só responde "de quem é esta rota".
 */
public final class CatalogoModulos {

    public enum TipoDono { INFRA, NUCLEO, MODULO, INTEGRACAO }

    /** Dono de uma rota: infraestrutura, núcleo (sempre ativo), um módulo ou uma integração (exige mais de um módulo). */
    public record Dono(TipoDono tipo, String id) {
        @Override
        public String toString() {
            return tipo.name().toLowerCase() + ":" + id;
        }
    }

    /** Uma linha de rota do catálogo: métodos (vazio = qualquer) e padrão de caminho no estilo Ant. */
    public record Entrada(Set<String> metodos, String padrao, Dono dono) {
    }

    /** Resultado de {@link #resolver}: os donos empatados na especificidade mais alta (normalmente um só). */
    public record Resolucao(List<Dono> donos, String padrao) {
        public boolean semDono() {
            return donos.isEmpty();
        }

        public Dono dono() {
            return donos.get(0);
        }
    }

    public record Bloqueio(String modulo, String motivo, String verificacao) {
    }

    public record Condicional(String modulo, String quando, String requer, String verificacao) {
    }

    private final int versao;
    private final List<String> ids;
    private final Map<String, Set<String>> requer;
    private final Map<String, Set<String>> recomenda;
    private final Map<String, List<String>> modulosDaIntegracao;
    private final Map<String, List<String>> porRelatorio;
    private final List<Entrada> entradas;
    private final List<Bloqueio> bloqueios;
    private final List<Condicional> condicionais;
    private final AntPathMatcher matcher = new AntPathMatcher();

    private CatalogoModulos(JsonNode raiz) {
        this.versao = raiz.get("versao").asInt();
        List<String> idsLidos = new ArrayList<>();
        Map<String, Set<String>> requerLido = new LinkedHashMap<>();
        Map<String, Set<String>> recomendaLido = new LinkedHashMap<>();
        Map<String, List<String>> integracoes = new LinkedHashMap<>();
        Map<String, List<String>> relatorios = new LinkedHashMap<>();
        List<Entrada> entradasLidas = new ArrayList<>();
        List<Condicional> condicionaisLidas = new ArrayList<>();

        adicionarRotas(entradasLidas, raiz.get("infra"), new Dono(TipoDono.INFRA, "infra"));
        adicionarRotas(entradasLidas, raiz.get("nucleo"), new Dono(TipoDono.NUCLEO, "nucleo"));
        for (JsonNode m : raiz.get("modulos")) {
            String id = m.get("id").asText();
            idsLidos.add(id);
            requerLido.put(id, new LinkedHashSet<>(textos(m, "requer")));
            recomendaLido.put(id, new LinkedHashSet<>(textos(m, "recomenda")));
            adicionarRotas(entradasLidas, m, new Dono(TipoDono.MODULO, id));
            if (m.has("porRelatorio")) {
                m.get("porRelatorio").properties().forEach(e -> relatorios.put(e.getKey(), textos(e.getValue())));
            }
            if (m.has("condicionais")) {
                for (JsonNode c : m.get("condicionais")) {
                    condicionaisLidas.add(new Condicional(id, c.get("quando").asText(), c.get("requer").asText(),
                            c.has("verificacao") ? c.get("verificacao").asText() : null));
                }
            }
        }
        for (JsonNode i : raiz.get("integracoes")) {
            String id = i.get("id").asText();
            integracoes.put(id, textos(i, "modulos"));
            adicionarRotas(entradasLidas, i, new Dono(TipoDono.INTEGRACAO, id));
        }
        List<Bloqueio> bloqueiosLidos = new ArrayList<>();
        for (JsonNode b : raiz.get("bloqueiosDeRemocao")) {
            bloqueiosLidos.add(new Bloqueio(b.get("modulo").asText(), b.get("motivo").asText(), b.get("verificacao").asText()));
        }
        this.ids = List.copyOf(idsLidos);
        this.requer = Collections.unmodifiableMap(requerLido);
        this.recomenda = Collections.unmodifiableMap(recomendaLido);
        this.modulosDaIntegracao = Collections.unmodifiableMap(integracoes);
        this.porRelatorio = Collections.unmodifiableMap(relatorios);
        this.entradas = List.copyOf(entradasLidas);
        this.bloqueios = List.copyOf(bloqueiosLidos);
        this.condicionais = List.copyOf(condicionaisLidas);
    }

    /** Carrega do classpath. Falha o boot se o catálogo estiver ilegível: sem catálogo não há como decidir acesso. */
    public static CatalogoModulos carregar() {
        try (InputStream in = new ClassPathResource("modulos/catalogo.json").getInputStream()) {
            return new CatalogoModulos(new ObjectMapper().readTree(in));
        } catch (IOException e) {
            throw new IllegalStateException("modulos/catalogo.json ilegível: o catálogo de módulos é obrigatório", e);
        }
    }

    private static void adicionarRotas(List<Entrada> destino, JsonNode no, Dono dono) {
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

    private static List<String> textos(JsonNode no, String campo) {
        return no != null && no.has(campo) ? textos(no.get(campo)) : List.of();
    }

    private static List<String> textos(JsonNode array) {
        List<String> r = new ArrayList<>();
        array.forEach(x -> r.add(x.asText()));
        return r;
    }

    public int versao() {
        return versao;
    }

    public List<String> idsDosModulos() {
        return ids;
    }

    public boolean existe(String id) {
        return ids.contains(id);
    }

    public Set<String> requer(String modulo) {
        return requer.getOrDefault(modulo, Set.of());
    }

    public Set<String> recomenda(String modulo) {
        return recomenda.getOrDefault(modulo, Set.of());
    }

    public Map<String, List<String>> modulosDasIntegracoes() {
        return modulosDaIntegracao;
    }

    /** Módulos de origem de cada tipo de relatório (ex.: {@code contas -> [financeiro]}). */
    public Map<String, List<String>> porRelatorio() {
        return porRelatorio;
    }

    public List<Entrada> entradas() {
        return entradas;
    }

    public List<Bloqueio> bloqueios() {
        return bloqueios;
    }

    public List<Condicional> condicionais() {
        return condicionais;
    }

    /**
     * Dono da rota. A entrada MAIS ESPECÍFICA vence; se nenhuma aceita o método, vale a mais específica só pelo caminho (uma rota
     * nunca escapa do módulo por causa do método, ex.: HEAD). {@code HEAD} conta como {@code GET}. Sem nenhuma entrada, a resolução vem
     * vazia.
     */
    public Resolucao resolver(String metodo, String caminho) {
        String m = "HEAD".equals(metodo) ? "GET" : metodo;
        List<Entrada> candidatas = filtrar(m, caminho, true);
        if (candidatas.isEmpty()) {
            candidatas = filtrar(m, caminho, false);
        }
        if (candidatas.isEmpty()) {
            return new Resolucao(List.of(), null);
        }
        Comparator<Entrada> especificidade = (a, b) -> matcher.getPatternComparator(caminho).compare(a.padrao(), b.padrao());
        List<Entrada> ordenadas = new ArrayList<>(candidatas);
        ordenadas.sort(especificidade);
        Entrada melhor = ordenadas.get(0);
        Set<Dono> empatados = new LinkedHashSet<>();
        for (Entrada e : ordenadas) {
            if (especificidade.compare(e, melhor) == 0 || e.padrao().equals(melhor.padrao())) {
                empatados.add(e.dono());
            }
        }
        return new Resolucao(List.copyOf(empatados), melhor.padrao());
    }

    private List<Entrada> filtrar(String metodo, String caminho, boolean exigirMetodo) {
        List<Entrada> r = new ArrayList<>();
        for (Entrada e : entradas) {
            boolean metodoOk = !exigirMetodo || e.metodos().isEmpty() || e.metodos().contains(metodo);
            if (metodoOk && matcher.match(e.padrao(), caminho)) {
                r.add(e);
            }
        }
        return r;
    }
}
