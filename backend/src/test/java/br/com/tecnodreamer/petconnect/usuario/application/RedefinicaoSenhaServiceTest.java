package br.com.tecnodreamer.petconnect.usuario.application;

import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;
import br.com.tecnodreamer.petconnect.usuario.domain.TokenRedefinicaoSenha;
import br.com.tecnodreamer.petconnect.usuario.domain.Usuario;
import br.com.tecnodreamer.petconnect.usuario.infrastructure.EmailService;
import br.com.tecnodreamer.petconnect.usuario.infrastructure.LimitadorRedefinicaoEmMemoria;
import br.com.tecnodreamer.petconnect.usuario.infrastructure.TokenRedefinicaoSenhaRepository;
import br.com.tecnodreamer.petconnect.usuario.infrastructure.UsuarioRepository;
import br.com.tecnodreamer.petconnect.usuario.web.RedefinirSenhaRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static br.com.tecnodreamer.petconnect.pet.PetFixtures.CLOCK;
import static br.com.tecnodreamer.petconnect.usuario.UsuarioFixtures.VENDAS;
import static br.com.tecnodreamer.petconnect.usuario.UsuarioFixtures.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedefinicaoSenhaServiceTest {

    static final String NOVA = "Nova@Senha2026";
    static final LocalDateTime AGORA = LocalDateTime.now(CLOCK);

    @Mock UsuarioRepository usuarios;
    @Mock TokenRedefinicaoSenhaRepository tokens;
    @Mock EmailService email;
    @Mock AuditoriaService auditoria;

    final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    RedefinicaoSenhaService service;
    Usuario ana;

    @BeforeEach
    void setUp() {
        service = new RedefinicaoSenhaService(usuarios, tokens, encoder, email, new LimitadorRedefinicaoEmMemoria(CLOCK),
                auditoria, CLOCK, "http://localhost:3000/");
        ana = usuario(10, "ana", VENDAS, encoder.encode("Antiga@Senha1"));
        when(usuarios.findByEmailIgnoreCase("ana@petconnect.test")).thenReturn(Optional.of(ana));
    }

    private String tokenEnviado() {
        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(email).enviar(eq("ana@petconnect.test"), anyString(), texto.capture());
        Matcher m = Pattern.compile("redefinir-senha\\?token=([A-Za-z0-9_-]+)").matcher(texto.getValue());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private TokenRedefinicaoSenha tokenSalvo() {
        ArgumentCaptor<TokenRedefinicaoSenha> captor = ArgumentCaptor.forClass(TokenRedefinicaoSenha.class);
        verify(tokens).save(captor.capture());
        return captor.getValue();
    }

    private void comToken(String token, LocalDateTime expira, LocalDateTime usado) {
        TokenRedefinicaoSenha t = new TokenRedefinicaoSenha(ana, RedefinicaoSenhaService.sha256(token), expira);
        if (usado != null) {
            t.marcarUsado(usado);
        }
        when(tokens.findByTokenHash(RedefinicaoSenhaService.sha256(token))).thenReturn(Optional.of(t));
    }

    @Test
    void deveGerarTokenComHashSha256() {
        service.solicitar("ana@petconnect.test", "10.0.0.1");

        String token = tokenEnviado();
        assertThat(token).hasSizeGreaterThanOrEqualTo(43);
        TokenRedefinicaoSenha salvo = tokenSalvo();
        assertThat(salvo.getTokenHash()).hasSize(64).isEqualTo(RedefinicaoSenhaService.sha256(token)).isNotEqualTo(token);
    }

    @Test
    void deveDefinirExpiracaoDeTrintaMinutos() {
        service.solicitar("ana@petconnect.test", "10.0.0.1");

        assertThat(tokenSalvo().getExpiraEm()).isEqualTo(AGORA.plusMinutes(30));
    }

    @Test
    void deveRedefinirSenhaComTokenValido() {
        comToken("tok-valido", AGORA.plusMinutes(10), null);

        service.redefinir(new RedefinirSenhaRequest("tok-valido", NOVA, NOVA));

        assertThat(encoder.matches(NOVA, ana.getSenhaHash())).isTrue();
        verify(auditoria).registrar(eq("ana"), eq("SENHA_REDEFINIDA"), eq("usuario"), eq(10L), any());
    }

    @Test
    void deveRejeitarTokenExpirado() {
        comToken("tok-expirado", AGORA.minusSeconds(1), null);

        assertThatThrownBy(() -> service.redefinir(new RedefinirSenhaRequest("tok-expirado", NOVA, NOVA)))
                .isInstanceOf(CampoInvalidoException.class).hasMessage("O link de redefinição é inválido ou expirou.")
                .extracting("campo").isEqualTo("token");
    }

    @Test
    void deveRejeitarTokenJaUsado() {
        comToken("tok-usado", AGORA.plusMinutes(10), AGORA.minusMinutes(1));

        assertThatThrownBy(() -> service.redefinir(new RedefinirSenhaRequest("tok-usado", NOVA, NOVA)))
                .isInstanceOf(CampoInvalidoException.class).extracting("campo").isEqualTo("token");
    }

    @Test
    void deveRejeitarTokenInexistente() {
        assertThatThrownBy(() -> service.redefinir(new RedefinirSenhaRequest("nao-existe", NOVA, NOVA)))
                .isInstanceOf(CampoInvalidoException.class).extracting("campo").isEqualTo("token");
    }

    @Test
    void deveInvalidarTokensAnterioresAoGerarNovo() {
        service.solicitar("ana@petconnect.test", "10.0.0.1");

        verify(tokens).invalidarPendentes(10L, AGORA);
    }

    @Test
    void deveIncrementarTokenVersionAoRedefinir() {
        comToken("tok-versao", AGORA.plusMinutes(10), null);

        service.redefinir(new RedefinirSenhaRequest("tok-versao", NOVA, NOVA));

        assertThat(ana.getTokenVersion()).isEqualTo(1);
        verify(tokens).invalidarPendentes(10L, AGORA);
    }

    @Test
    void deveRetornarRespostaNeutraParaEmailInexistente() {
        assertThatCode(() -> service.solicitar("ninguem@petconnect.test", "10.0.0.1")).doesNotThrowAnyException();

        verify(tokens, never()).save(any());
        verify(email, never()).enviar(anyString(), anyString(), anyString());
    }

    @Test
    void deveRespeitarRateLimitPorEmail() {
        for (int i = 0; i < 4; i++) {
            service.solicitar("ana@petconnect.test", "10.0.0." + i);
        }

        verify(tokens, times(3)).save(any());
    }

    @Test
    void deveRespeitarRateLimitPorIp() {
        when(usuarios.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(ana));
        for (int i = 0; i < 11; i++) {
            service.solicitar("pessoa" + i + "@petconnect.test", "10.9.9.9");
        }

        verify(tokens, times(10)).save(any());
        verify(tokens, times(10)).invalidarPendentes(anyLong(), any());
    }
}
