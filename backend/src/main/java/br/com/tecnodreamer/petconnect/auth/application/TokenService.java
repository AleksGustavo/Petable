package br.com.tecnodreamer.petconnect.auth.application;

import br.com.tecnodreamer.petconnect.shared.config.SecurityConfig;
import br.com.tecnodreamer.petconnect.shared.config.SecurityProperties;
import br.com.tecnodreamer.petconnect.usuario.domain.Usuario;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final SecurityProperties props;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, SecurityProperties props, Clock clock) {
        this.encoder = encoder;
        this.props = props;
        this.clock = clock;
    }

    public String emitir(Usuario usuario) {
        Instant agora = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.jwt().issuer())
                .subject(usuario.getUsername())
                .issuedAt(agora)
                .expiresAt(agora.plus(props.jwt().ttl()))
                .claim("uid", usuario.getId())
                .claim(SecurityConfig.ROLES_CLAIM, List.of(usuario.getPapel().getNome()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long validadeEmSegundos() {
        return props.jwt().ttl().toSeconds();
    }
}
