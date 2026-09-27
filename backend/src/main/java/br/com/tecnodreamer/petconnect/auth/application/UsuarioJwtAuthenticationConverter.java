package br.com.tecnodreamer.petconnect.auth.application;

import br.com.tecnodreamer.petconnect.usuario.domain.Usuario;
import br.com.tecnodreamer.petconnect.usuario.infrastructure.UsuarioRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Depois de validar assinatura e expiração, confere o token contra o banco a
 * cada requisição (etapa 03, DA-05): o usuário existe, está ativo e a versão
 * da credencial ({@code tv}) é a atual. O papel usado na autorização é o do
 * banco, não o da claim. Qualquer divergência vira 401.
 */
@Component
public class UsuarioJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UsuarioRepository usuarios;

    public UsuarioJwtAuthenticationConverter(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        if (!(jwt.getClaim("uid") instanceof Number uid) || !(jwt.getClaim("tv") instanceof Number tv)) {
            throw new InvalidBearerTokenException("Sessão inválida.");
        }
        Usuario u = usuarios.findById(uid.longValue())
                .filter(Usuario::isAtivo)
                .filter(x -> x.getTokenVersion() == tv.intValue())
                .orElseThrow(() -> new InvalidBearerTokenException("Sessão expirada ou revogada."));
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + u.getPapel().getNome())),
                u.getUsername());
    }
}
