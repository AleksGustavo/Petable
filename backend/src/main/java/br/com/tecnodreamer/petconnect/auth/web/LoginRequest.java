package br.com.tecnodreamer.petconnect.auth.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 120) String login,
        @NotBlank @Size(max = 128) String senha) {
}
