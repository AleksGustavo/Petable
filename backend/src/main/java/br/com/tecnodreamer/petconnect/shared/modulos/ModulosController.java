package br.com.tecnodreamer.petconnect.shared.modulos;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Capacidades da instalação para o frontend montar menu, abas e botões. Só leitura e sanitizado: ids de módulos, nada de segredo nem de
 * dado de cliente. É conveniência de interface: a barreira real é o {@link ModulosFilter} na API.
 */
@RestController
public class ModulosController {

    private final ModulosAtivos modulos;

    public ModulosController(ModulosAtivos modulos) {
        this.modulos = modulos;
    }

    public record CapacidadesResponse(boolean todos, Set<String> modulos, List<String> integracoes, List<String> relatorios, int versaoCatalogo) {
    }

    @GetMapping("/api/v1/modulos")
    @PreAuthorize("isAuthenticated()")
    public CapacidadesResponse capacidades() {
        List<String> integracoes = modulos.catalogo().modulosDasIntegracoes().keySet().stream().filter(modulos::integracaoAtiva).sorted()
                .collect(Collectors.toList());
        return new CapacidadesResponse(modulos.modoTodos(), modulos.ativos(), integracoes, modulos.relatoriosDisponiveis(),
                modulos.catalogo().versao());
    }
}
