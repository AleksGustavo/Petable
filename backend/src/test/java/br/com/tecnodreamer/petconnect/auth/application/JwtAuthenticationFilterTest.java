package br.com.tecnodreamer.petconnect.auth.application;

import br.com.tecnodreamer.petconnect.usuario.domain.Usuario;
import br.com.tecnodreamer.petconnect.usuario.infrastructure.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static br.com.tecnodreamer.petconnect.usuario.UsuarioFixtures.ADMIN;
import static br.com.tecnodreamer.petconnect.usuario.UsuarioFixtures.VENDAS;
import static br.com.tecnodreamer.petconnect.usuario.UsuarioFixtures.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * A validação por requisição mora no conversor do resource server
 * ({@link UsuarioJwtAuthenticationConverter}), que roda dentro do filtro de
 * Bearer token do Spring Security — daí o nome do teste pedido na spec.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtAuthenticationFilterTest {

    @Mock UsuarioRepository usuarios;

    UsuarioJwtAuthenticationConverter conversor;
    Usuario ana;

    @BeforeEach
    void setUp() {
        conversor = new UsuarioJwtAuthenticationConverter(usuarios);
        ana = usuario(10, "ana", VENDAS, "h");
        when(usuarios.findById(10L)).thenReturn(Optional.of(ana));
    }

    private static Jwt jwt(int versao, String papelNaClaim) {
        return Jwt.withTokenValue("t").header("alg", "HS256").subject("ana").claim("uid", 10L).claim("tv", versao)
                .claim("roles", List.of(papelNaClaim)).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }

    @Test
    void deveAceitarTokenComVersaoAtual() {
        var auth = conversor.convert(jwt(0, "VENDAS"));

        assertThat(auth.getName()).isEqualTo("ana");
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_VENDAS");
    }

    @Test
    void deveRejeitarTokenComVersaoAntiga() {
        ana.definirSenha("outro-hash");

        assertThatThrownBy(() -> conversor.convert(jwt(0, "VENDAS"))).isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void deveRejeitarTokenDeUsuarioInativo() {
        ana.desativar();

        assertThatThrownBy(() -> conversor.convert(jwt(ana.getTokenVersion(), "VENDAS")))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void deveUsarPapelAtualDoUsuario() {
        // Claim diz ADMIN, mas o banco diz VENDAS: vale o banco.
        var auth = conversor.convert(jwt(0, "ADMIN"));

        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_VENDAS");
        assertThat(ADMIN.getNome()).isEqualTo("ADMIN");
    }

    @Test
    void deveRejeitarTokenSemVersao() {
        Jwt semTv = Jwt.withTokenValue("t").header("alg", "HS256").claim("uid", 10L).build();

        assertThatThrownBy(() -> conversor.convert(semTv)).isInstanceOf(InvalidBearerTokenException.class);
    }
}
