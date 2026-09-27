package br.com.tecnodreamer.petconnect.migracao.application;

import br.com.tecnodreamer.petconnect.migracao.infrastructure.DestinoMigracao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MigradorUsuarioTest {

    static final String SHA256_ADMIN123 = "240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9";

    @Mock DestinoMigracao destino;
    @Mock PasswordEncoder encoder;

    MigradorUsuario migrador;
    ContextoFake ctx = new ContextoFake().mapear("papel", 2, 2);

    @BeforeEach
    void setUp() {
        migrador = new MigradorUsuario(destino, encoder, "admin");
        when(destino.papelId("ADMIN")).thenReturn(Optional.of(1L));
        when(destino.papelId("VENDAS")).thenReturn(Optional.of(2L));
        when(destino.inserir(eq("usuario"), anyMap())).thenReturn(10L);
        when(encoder.encode(anyString())).thenReturn("$2a$12$hashbcrypt");
    }

    Linha usuario(String username, String email, long papel, int superUsuario) {
        return Linha.of("id", 7, "usuario", username, "email", email, "papel_id", papel, "ativo", 1,
                "super_usuario", superUsuario, "senha_hash", SHA256_ADMIN123);
    }

    Map<String, Object> gravado() {
        ArgumentCaptor<Map<String, Object>> c = ArgumentCaptor.captor();
        verify(destino).inserir(eq("usuario"), c.capture());
        return c.getValue();
    }

    @Test
    void naoDeveMigrarHashSha256() {
        migrador.migrar(usuario("maria", "maria@exemplo.test", 2, 0), ctx);

        assertThat(migrador.consulta()).doesNotContain("senha_hash").doesNotContain("token");
        assertThat(gravado().get("senha_hash")).isEqualTo("$2a$12$hashbcrypt").isNotEqualTo(SHA256_ADMIN123);
        verify(encoder, never()).encode(SHA256_ADMIN123);
    }

    @Test
    void deveGerarBcryptDeSegredoAleatorio() {
        migrador.migrar(usuario("maria", "maria@exemplo.test", 2, 0), ctx);
        migrador.migrar(usuario("jose", "jose@exemplo.test", 2, 0), ctx);

        ArgumentCaptor<String> segredos = ArgumentCaptor.forClass(String.class);
        verify(encoder, org.mockito.Mockito.times(2)).encode(segredos.capture());
        assertThat(segredos.getAllValues()).doesNotHaveDuplicates().allSatisfy(s -> assertThat(s).hasSizeGreaterThanOrEqualTo(43));
    }

    @Test
    void deveRenomearUsernameQueColideComBootstrap() {
        when(destino.usernameExiste("Admin_legado")).thenReturn(true);

        Desfecho d = migrador.migrar(usuario("Admin", "dono@exemplo.test", 2, 0), ctx);

        assertThat(gravado().get("username")).isEqualTo("Admin_legado_2");
        assertThat(d.situacao()).isEqualTo(Situacao.AJUSTADO);
        assertThat(d.pendencias()).contains("USERNAME_RENOMEADO");
    }

    @Test
    void superUsuarioDeveVirarAdmin() {
        migrador.migrar(usuario("dono", "dono@exemplo.test", 2, 1), ctx);

        assertThat(gravado().get("papel_id")).isEqualTo(1L);
    }

    @Test
    void usuarioSemEmailDeveFicarInativo() {
        Desfecho d = migrador.migrar(usuario("joao", "", 2, 0), ctx);

        Map<String, Object> v = gravado();
        assertThat(v.get("ativo")).isEqualTo(false);
        assertThat(v.get("email")).isEqualTo("legado-7@migracao.invalid");
        assertThat(d.pendencias()).containsExactly("USUARIO_SEM_EMAIL_RECUPERACAO");
    }
}
