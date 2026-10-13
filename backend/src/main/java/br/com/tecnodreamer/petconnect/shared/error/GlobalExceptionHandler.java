package br.com.tecnodreamer.petconnect.shared.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/** Todas as respostas de erro seguem RFC 9457 (application/problem+json). */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Recurso não encontrado", ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException ex) {
        ProblemDetail body = problem(HttpStatus.CONFLICT, "Conflito", ex.getMessage());
        ex.getPropriedades().forEach(body::setProperty);
        return body;
    }

    @ExceptionHandler(CampoInvalidoException.class)
    ProblemDetail campoInvalido(CampoInvalidoException ex) {
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Dados inválidos", ex.getMessage());
        body.setProperty("errors", Map.of(ex.getCampo(), ex.getMessage()));
        return body;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrity(DataIntegrityViolationException ex) {
        log.warn("Violação de integridade: {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "Conflito",
                "A operação viola uma restrição de dados (duplicidade ou vínculo).");
    }

    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail badCredentials(BadCredentialsException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "Não autenticado", "Usuário ou senha inválidos.");
    }

    @ExceptionHandler(LimiteExcedidoException.class)
    ResponseEntity<ProblemDetail> limite(LimiteExcedidoException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(org.springframework.http.HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfter()))
                .body(problem(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas", ex.getMessage()));
    }

    @ExceptionHandler(LockedException.class)
    ProblemDetail locked(LockedException ex) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas", ex.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Dados inválidos", "Um ou mais campos são inválidos.");
        body.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    /** Upload acima do limite do servidor: mesmo formato (400 no campo) do limite de 5 MB da foto. */
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Dados inválidos", "A foto deve ter no máximo 5 MB.");
        body.setProperty("errors", Map.of("foto", "A foto deve ter no máximo 5 MB."));
        return ResponseEntity.badRequest().body(body);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }
}
