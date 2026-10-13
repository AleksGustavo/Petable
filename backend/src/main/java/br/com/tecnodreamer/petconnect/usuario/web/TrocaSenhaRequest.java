package br.com.tecnodreamer.petconnect.usuario.web;

import jakarta.validation.constraints.NotBlank;

public record TrocaSenhaRequest(
        @NotBlank(message = "Informe a senha atual") String senhaAtual,
        @NotBlank(message = "Informe a nova senha") String novaSenha,
        @NotBlank(message = "Confirme a nova senha") String confirmacaoNovaSenha) {
}
