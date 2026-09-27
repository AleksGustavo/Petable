package br.com.tecnodreamer.petconnect.usuario.application;

import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;
import br.com.tecnodreamer.petconnect.usuario.domain.Usuario;
import br.com.tecnodreamer.petconnect.usuario.infrastructure.TokenRedefinicaoSenhaRepository;
import br.com.tecnodreamer.petconnect.usuario.infrastructure.UsuarioRepository;
import br.com.tecnodreamer.petconnect.usuario.web.TrocaSenhaRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static br.com.tecnodreamer.petconnect.pet.PetFixtures.CLOCK;
import static br.com.tecnodreamer.petconnect.usuario.UsuarioFixtures.VENDAS;
import static br.com.tecnodreamer.petconnect.usuario.UsuarioFixtures.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TrocaSenhaServiceTest {

    static final String ATUAL = "Senha@Atual2026";
    static final String NOVA = "Nova@Senha2026";

    @Mock UsuarioRepository usuarios;
    @Mock TokenRedefinicaoSenhaRepository tokens;
    @Mock AuditoriaService auditoria;

    final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    TrocaSenhaService service;
    Usuario ana;

    @BeforeEach
    void setUp() {
        service = new TrocaSenhaService(usuarios, tokens, encoder, auditoria, CLOCK);
        ana = usuario(10, "ana", VENDAS, encoder.encode(ATUAL));
        when(usuarios.findById(10L)).thenReturn(Optional.of(ana));
    }

    @Test
    void deveTrocarSenhaComSenhaAtualCorreta() {
        service.trocar(10L, new TrocaSenhaRequest(ATUAL, NOVA, NOVA));

        assertThat(encoder.matches(NOVA, ana.getSenhaHash())).isTrue();
        verify(auditoria).registrar(eq("ana"), eq("SENHA_ALTERADA"), eq("usuario"), eq(10L), anyMap());
    }

    @Test
    void deveRejeitarSenhaAtualIncorreta() {
        assertThatThrownBy(() -> service.trocar(10L, new TrocaSenhaRequest("Errada@2026x", NOVA, NOVA)))
                .isInstanceOf(CampoInvalidoException.class).extracting("campo").isEqualTo("senhaAtual");
        assertThat(ana.getTokenVersion()).isZero();
    }

    @Test
    void deveRejeitarNovaSenhaIgualAAtual() {
        assertThatThrownBy(() -> service.trocar(10L, new TrocaSenhaRequest(ATUAL, ATUAL, ATUAL)))
                .isInstanceOf(CampoInvalidoException.class).extracting("campo").isEqualTo("novaSenha");
        verify(tokens, never()).invalidarPendentes(anyLong(), any());
    }

    @Test
    void deveIncrementarTokenVersionAoTrocarSenha() {
        service.trocar(10L, new TrocaSenhaRequest(ATUAL, NOVA, NOVA));

        assertThat(ana.getTokenVersion()).isEqualTo(1);
    }

    @Test
    void deveInvalidarTokensDeRedefinicaoPendentes() {
        service.trocar(10L, new TrocaSenhaRequest(ATUAL, NOVA, NOVA));

        verify(tokens).invalidarPendentes(10L, LocalDateTime.now(CLOCK));
    }

    @Test
    void deveRejeitarConfirmacaoDiferenteEPoliticaFraca() {
        assertThatThrownBy(() -> service.trocar(10L, new TrocaSenhaRequest(ATUAL, NOVA, NOVA + "x")))
                .isInstanceOf(CampoInvalidoException.class).extracting("campo").isEqualTo("confirmacaoNovaSenha");
        assertThatThrownBy(() -> service.trocar(10L, new TrocaSenhaRequest(ATUAL, "fraca", "fraca")))
                .isInstanceOf(CampoInvalidoException.class).extracting("campo").isEqualTo("novaSenha");
    }
}
