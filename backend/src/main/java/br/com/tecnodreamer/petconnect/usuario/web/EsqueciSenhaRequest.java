package br.com.tecnodreamer.petconnect.usuario.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EsqueciSenhaRequest(@NotBlank(message = "Informe o e-mail") @Email(message = "E-mail inválido") @Size(max = 120) String email) {
}
