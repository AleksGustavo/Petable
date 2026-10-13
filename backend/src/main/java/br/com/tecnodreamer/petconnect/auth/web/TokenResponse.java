package br.com.tecnodreamer.petconnect.auth.web;

public record TokenResponse(String accessToken, String tokenType, long expiresIn, UsuarioLogado usuario) {

    public record UsuarioLogado(Long id, String username, String email, String papel) {
    }
}
