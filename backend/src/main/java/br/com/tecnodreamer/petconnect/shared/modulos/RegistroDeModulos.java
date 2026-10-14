package br.com.tecnodreamer.petconnect.shared.modulos;

import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.modulos.CatalogoModulos.Bloqueio;
import br.com.tecnodreamer.petconnect.shared.modulos.CatalogoModulos.Condicional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Validações de módulo que precisam do banco, feitas na partida (depois do Flyway): (1) a REMOÇÃO de um módulo em relação à partida
 * anterior não pode violar operação pendente (política de downgrade: nunca apaga dado e bloqueia o que deixaria algo órfão), e (2) as
 * dependências condicionais (ex.: a agenda com serviço de vacinação ativo exige a clínica). Falhou, o sistema NÃO sobe e a mensagem diz
 * o que resolver. Depois de validado, registra a lista efetiva no histórico e na auditoria.
 */
@Component
public class RegistroDeModulos implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RegistroDeModulos.class);

    private final JdbcTemplate jdbc;
    private final ModulosAtivos modulos;
    private final AuditoriaService auditoria;

    public RegistroDeModulos(JdbcTemplate jdbc, ModulosAtivos modulos, AuditoriaService auditoria) {
        this.jdbc = jdbc;
        this.modulos = modulos;
        this.auditoria = auditoria;
    }

    @Override
    public void run(ApplicationArguments args) {
        Set<String> atuais = modulos.ativos();
        Set<String> anteriores = ultimaLista();
        List<String> violacoes = new ArrayList<>(violacoesDeRemocao(anteriores, atuais));
        violacoes.addAll(violacoesCondicionais(atuais));
        if (!violacoes.isEmpty()) {
            throw new ModulosInvalidosException("Configuração de módulos recusada: " + String.join(" | ", violacoes)
                    + ". Nenhum dado foi alterado. Restaure a lista anterior de PETCONNECT_MODULOS ou resolva a pendência.");
        }
        if (anteriores == null || !anteriores.equals(atuais)) {
            jdbc.update("insert into modulo_instalacao_historico (modulos, modo_todos, versao_catalogo) values (?, ?, ?)",
                    String.join(",", atuais), modulos.modoTodos(), modulos.catalogo().versao());
            Map<String, Object> detalhes = new LinkedHashMap<>();
            detalhes.put("modulos", String.join(",", atuais));
            detalhes.put("anteriores", anteriores == null ? null : String.join(",", anteriores));
            detalhes.put("modoTodos", modulos.modoTodos());
            auditoria.registrarSempre("sistema", "MODULOS_ALTERADOS", "instalacao", "modulos", detalhes);
            log.info("Lista de módulos registrada: {}", String.join(",", atuais));
        }
    }

    /** Última lista registrada, ou {@code null} na primeira partida. */
    Set<String> ultimaLista() {
        List<String> linhas = jdbc.queryForList("select modulos from modulo_instalacao_historico order by id desc limit 1", String.class);
        if (linhas.isEmpty()) {
            return null;
        }
        String csv = linhas.get(0);
        return csv.isBlank() ? new TreeSet<>() : new TreeSet<>(Arrays.asList(csv.split(",")));
    }

    /** Módulos que saíram da lista e ainda têm pendência (consulta do catálogo com contagem maior que zero). */
    public List<String> violacoesDeRemocao(Set<String> anteriores, Set<String> atuais) {
        List<String> r = new ArrayList<>();
        if (anteriores == null) {
            return r;
        }
        for (String removido : anteriores) {
            if (atuais.contains(removido) || !modulos.catalogo().existe(removido)) {
                continue;
            }
            for (Bloqueio b : modulos.catalogo().bloqueios()) {
                if (!b.modulo().equals(removido) || !b.verificacao().trim().toLowerCase().startsWith("select")) {
                    continue; // as demais (dependência) já são cobertas pelo fecho de dependências
                }
                Long pendentes = jdbc.queryForObject(b.verificacao(), Long.class);
                if (pendentes != null && pendentes > 0) {
                    r.add("não é possível remover '" + removido + "': " + b.motivo() + " (" + pendentes + ")");
                }
            }
        }
        return r;
    }

    /** Dependências condicionais do catálogo não satisfeitas pela lista atual. */
    public List<String> violacoesCondicionais(Set<String> atuais) {
        List<String> r = new ArrayList<>();
        for (Condicional c : modulos.catalogo().condicionais()) {
            if (!atuais.contains(c.modulo()) || atuais.contains(c.requer()) || c.verificacao() == null) {
                continue;
            }
            Long ocorrencias = jdbc.queryForObject(c.verificacao(), Long.class);
            if (ocorrencias != null && ocorrencias > 0) {
                r.add("o módulo '" + c.modulo() + "' exige '" + c.requer() + "' enquanto houver " + c.quando());
            }
        }
        return r;
    }
}
