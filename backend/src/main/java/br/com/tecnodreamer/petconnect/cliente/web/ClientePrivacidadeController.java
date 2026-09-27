package br.com.tecnodreamer.petconnect.cliente.web;

import br.com.tecnodreamer.petconnect.cliente.application.ClientePrivacidadeService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** LGPD: exportação e anonimização do titular (só ADMIN). */
@RestController
@RequestMapping("/api/v1/clientes/{id}")
@PreAuthorize("hasRole('ADMIN')")
public class ClientePrivacidadeController {

    public record AnonimizacaoRequest(String motivo) {
    }

    private final ClientePrivacidadeService service;

    public ClientePrivacidadeController(ClientePrivacidadeService service) {
        this.service = service;
    }

    @GetMapping("/dados-pessoais")
    public ResponseEntity<Map<String, Object>> exportar(@PathVariable Long id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("dados-cliente-" + id + ".json").build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(service.exportar(id));
    }

    @PostMapping("/anonimizar")
    public ResponseEntity<Void> anonimizar(@PathVariable Long id, @RequestBody AnonimizacaoRequest req) {
        service.anonimizar(id, req.motivo());
        return ResponseEntity.noContent().build();
    }
}
