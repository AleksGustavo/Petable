package br.com.tecnodreamer.petconnect.usuario.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Criação de conta (só ADMIN). A política de senha é conferida no serviço. */
public record UsuarioRequest(
        @NotBlank(message = "Informe o nome de usuário")
        @Size(min = 3, max = 50, message = "O nome de usuário deve ter entre 3 e 50 caracteres")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Use apenas letras, números, ponto, hífen e sublinhado") String username,
        @NotBlank(message = "Informe o e-mail") @Email(message = "E-mail inválido") @Size(max = 120) String email,
        @NotBlank(message = "Informe a senha") String senha,
        @NotBlank(message = "Informe o papel")
        @Pattern(regexp = "ADMIN|VENDAS|SERVICO", message = "Papel inválido") String papel) {
}
