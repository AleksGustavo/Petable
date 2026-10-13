package br.com.tecnodreamer.petconnect.shared.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

/** Id do usuário da requisição atual (claim {@code uid} do JWT), quando houver. */
public final class UsuarioAtual {

    private UsuarioAtual() {
    }

    public static Optional<Long> id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt && jwt.getToken().getClaim("uid") instanceof Number uid) {
            return Optional.of(uid.longValue());
        }
        return Optional.empty();
    }
}
