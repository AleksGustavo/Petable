package br.com.tecnodreamer.petconnect.usuario.web;

import br.com.tecnodreamer.petconnect.usuario.application.RedefinicaoSenhaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Rotas públicas de recuperação de senha. */
@RestController
@RequestMapping("/api/v1/auth")
public class RecuperacaoSenhaController {

    static final String MENSAGEM_NEUTRA =
            "Se existir uma conta ativa para o e-mail informado, enviaremos as instruções de redefinição.";

    private final RedefinicaoSenhaService service;

    public RecuperacaoSenhaController(RedefinicaoSenhaService service) {
        this.service = service;
    }

    /** Sempre 202 com a mesma mensagem: não revela se o e-mail existe nem se o limite foi atingido. */
    @PostMapping("/esqueci-senha")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> esqueciSenha(@Valid @RequestBody EsqueciSenhaRequest request, HttpServletRequest http) {
        service.solicitar(request.email(), ipDoCliente(http));
        return Map.of("message", MENSAGEM_NEUTRA);
    }

    @PostMapping("/redefinir-senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void redefinirSenha(@Valid @RequestBody RedefinirSenhaRequest request) {
        service.redefinir(request);
    }

    /**
     * O navegador fala com o Next (BFF), que repassa o IP original em
     * X-Forwarded-For. Sem o cabeçalho, usa o endereço da conexão.
     */
    public static String ipDoCliente(HttpServletRequest http) {
        String xff = http.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}
