package br.com.tecnodreamer.petconnect.usuario.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RedefinirSenhaRequest(
        @NotBlank(message = "O link de redefinição é inválido ou expirou.") @Size(max = 200) String token,
        @NotBlank(message = "Informe a nova senha") String novaSenha,
        @NotBlank(message = "Confirme a nova senha") String confirmacaoNovaSenha) {
}
