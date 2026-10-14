package br.com.tecnodreamer.petconnect.shared.modulos;

import br.com.tecnodreamer.petconnect.shared.modulos.CatalogoModulos.Dono;
import br.com.tecnodreamer.petconnect.shared.modulos.CatalogoModulos.Resolucao;
import br.com.tecnodreamer.petconnect.shared.modulos.CatalogoModulos.TipoDono;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Fonte de verdade dos módulos contratados nesta instalação (etapa 07). Vem SÓ da configuração do servidor, validada no boot; nunca de
 * cabeçalho, parâmetro, cookie, corpo ou claim do token. Conjunto imutável: mudar a lista exige reiniciar a API (sem recarga a quente),
 * o que também descarta qualquer estado em memória. O token do usuário não carrega módulos, então nada a revogar.
 */
@Component
@EnableConfigurationProperties(ModulosProperties.class)
public class ModulosAtivos {

    private static final Logger log = LoggerFactory.getLogger(ModulosAtivos.class);
    public static final String TODOS = "todos";

    private final CatalogoModulos catalogo;
    private final boolean modoTodos;
    private final Set<String> ativos;

    @Autowired
    public ModulosAtivos(ModulosProperties propriedades) {
        this(propriedades.modulos(), CatalogoModulos.carregar());
    }

    public ModulosAtivos(String configurado, CatalogoModulos catalogo) {
        this.catalogo = catalogo;
        String texto = configurado == null ? "" : configurado.trim();
        this.modoTodos = TODOS.equalsIgnoreCase(texto);
        this.ativos = Collections.unmodifiableSet(analisar(texto, catalogo));
        if (modoTodos) {
            log.warn("PETCONNECT_MODULOS=todos: todos os módulos estão ativos. Em instalação nova de cliente, defina a lista contratada.");
        } else {
            log.info("Módulos ativos ({}): {}", ativos.size(), String.join(",", ativos));
        }
    }

    /** Interpreta e valida a configuração; junta todos os problemas numa só mensagem. */
    static Set<String> analisar(String texto, CatalogoModulos catalogo) {
        if (texto.isEmpty()) {
            throw new ModulosInvalidosException("PETCONNECT_MODULOS está vazio. Use 'todos' ou liste os módulos contratados, ex.: pdv,caixa,produtos,estoque. "
                    + "Módulos existentes: " + String.join(", ", catalogo.idsDosModulos()) + ".");
        }
        if (TODOS.equalsIgnoreCase(texto)) {
            return new LinkedHashSet<>(catalogo.idsDosModulos());
        }
        Set<String> lista = new LinkedHashSet<>();
        List<String> desconhecidos = new ArrayList<>();
        for (String bruto : texto.split("[,;\\s]+")) {
            String id = bruto.trim().toLowerCase(Locale.ROOT);
            if (id.isEmpty() || id.equals("nucleo") || id.equals("núcleo")) {
                continue; // o núcleo é implícito
            }
            if (id.equals(TODOS)) {
                throw new ModulosInvalidosException("'todos' não pode ser misturado com ids de módulos em PETCONNECT_MODULOS.");
            }
            if (!catalogo.existe(id)) {
                desconhecidos.add(bruto.trim());
            } else {
                lista.add(id);
            }
        }
        List<String> problemas = new ArrayList<>();
        if (!desconhecidos.isEmpty()) {
            problemas.add("módulo(s) desconhecido(s): " + String.join(", ", desconhecidos) + " (existentes: "
                    + String.join(", ", catalogo.idsDosModulos()) + ")");
        }
        for (String id : lista) {
            for (String dep : catalogo.requer(id)) {
                if (!lista.contains(dep)) {
                    problemas.add("o módulo '" + id + "' exige '" + dep + "'");
                }
            }
        }
        if (!problemas.isEmpty()) {
            throw new ModulosInvalidosException("PETCONNECT_MODULOS inválido: " + String.join("; ", problemas) + ".");
        }
        return lista;
    }

    public boolean modoTodos() {
        return modoTodos;
    }

    /** Ids dos módulos ativos (sem o núcleo, que é sempre ativo), em ordem alfabética. */
    public Set<String> ativos() {
        return Collections.unmodifiableSet(new TreeSet<>(ativos));
    }

    public boolean ativo(String modulo) {
        return ativos.contains(modulo);
    }

    public boolean integracaoAtiva(String integracao) {
        List<String> exigidos = catalogo.modulosDasIntegracoes().get(integracao);
        return exigidos != null && ativos.containsAll(exigidos);
    }

    /** Tipos de relatório cuja origem de dados está contratada. */
    public List<String> relatoriosDisponiveis() {
        if (!ativos.contains("relatorios")) {
            return List.of();
        }
        return catalogo.porRelatorio().entrySet().stream().filter(e -> ativos.containsAll(e.getValue())).map(java.util.Map.Entry::getKey).sorted().toList();
    }

    public boolean relatorioDisponivel(String tipo) {
        if (!ativos.contains("relatorios")) {
            return false;
        }
        List<String> origem = catalogo.porRelatorio().get(tipo);
        return origem != null && ativos.containsAll(origem);
    }

    /** A rota pode ser atendida nesta instalação? Núcleo e infraestrutura sempre; módulo e integração só se contratados. */
    public boolean permite(Resolucao r, String caminho) {
        if (r.semDono()) {
            return true; // sem dono = rota inexistente: o MVC responde 404 (o catálogo é conferido em teste contra as rotas reais)
        }
        for (Dono d : r.donos()) {
            if (!dono(d)) {
                return false;
            }
        }
        // Relatório: além do módulo "relatorios", o módulo de origem do tipo pedido precisa existir.
        if (r.dono().tipo() == TipoDono.MODULO && r.dono().id().equals("relatorios")) {
            String tipo = tipoDoRelatorio(caminho);
            return tipo == null || !catalogo.porRelatorio().containsKey(tipo) || relatorioDisponivel(tipo);
        }
        return true;
    }

    private boolean dono(Dono d) {
        return switch (d.tipo()) {
            case INFRA, NUCLEO -> true;
            case MODULO -> ativos.contains(d.id());
            case INTEGRACAO -> integracaoAtiva(d.id());
        };
    }

    /** {@code /api/v1/relatorios/{tipo}} ou {@code /api/v1/relatorios/{tipo}/csv} → tipo. */
    static String tipoDoRelatorio(String caminho) {
        String prefixo = "/api/v1/relatorios/";
        if (!caminho.startsWith(prefixo)) {
            return null;
        }
        String resto = caminho.substring(prefixo.length());
        int barra = resto.indexOf('/');
        return barra < 0 ? resto : resto.substring(0, barra);
    }

    public CatalogoModulos catalogo() {
        return catalogo;
    }

    @Override
    public String toString() {
        return "ModulosAtivos" + Arrays.toString(ativos().toArray());
    }
}
