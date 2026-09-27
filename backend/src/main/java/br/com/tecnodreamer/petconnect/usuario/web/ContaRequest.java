package br.com.tecnodreamer.petconnect.usuario.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Dados de conta editáveis (admin ou o próprio usuário). Não aceita senha, papel nem ativo. */
public record ContaRequest(
        @NotBlank(message = "Informe o nome de usuário")
        @Size(min = 3, max = 50, message = "O nome de usuário deve ter entre 3 e 50 caracteres")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Use apenas letras, números, ponto, hífen e sublinhado") String username,
        @NotBlank(message = "Informe o e-mail") @Email(message = "E-mail inválido") @Size(max = 120) String email) {
}
