package br.com.tecnodreamer.petconnect.pet.web;

import br.com.tecnodreamer.petconnect.pet.application.VacinaEstoqueService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Vínculo entre vacina do catálogo e produto de estoque. Alterar: só ADMIN. Ler as vacinas de um produto: ADMIN e VENDAS. */
@RestController
public class VacinaEstoqueController {

    private final VacinaEstoqueService service;

    public VacinaEstoqueController(VacinaEstoqueService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/vacinas/estoque")
    @PreAuthorize("hasRole('ADMIN')")
    public List<VacinaEstoqueResponse> listar() {
        return service.listar();
    }

    @PutMapping("/api/v1/vacinas/{id}/produto")
    @PreAuthorize("hasRole('ADMIN')")
    public VacinaEstoqueResponse vincular(@PathVariable Long id, @Valid @RequestBody VinculoProdutoRequest request) {
        return service.vincular(id, request.produtoId());
    }

    @DeleteMapping("/api/v1/vacinas/{id}/produto")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desvincular(@PathVariable Long id) {
        service.desvincular(id);
    }

    @GetMapping("/api/v1/produtos/{id}/vacinas")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDAS')")
    public List<VacinaEstoqueResponse> doProduto(@PathVariable Long id) {
        return service.doProduto(id);
    }
}
