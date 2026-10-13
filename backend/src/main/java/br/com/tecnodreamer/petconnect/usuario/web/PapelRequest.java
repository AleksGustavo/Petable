package br.com.tecnodreamer.petconnect.usuario.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PapelRequest(
        @NotBlank(message = "Informe o papel") @Pattern(regexp = "ADMIN|VENDAS|SERVICO", message = "Papel inválido") String papel) {
}
