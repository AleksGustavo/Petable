package br.com.tecnodreamer.petconnect.usuario.application;

import br.com.tecnodreamer.petconnect.funcionario.infrastructure.FuncionarioRepository;
import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;
import br.com.tecnodreamer.petconnect.shared.error.ConflictException;
import br.com.tecnodreamer.petconnect.usuario.domain.Usuario;
import br.com.tecnodreamer.petconnect.usuario.infrastructure.PapelRepository;
import br.com.tecnodreamer.petconnect.usuario.infrastructure.UsuarioRepository;
import br.com.tecnodreamer.petconnect.usuario.web.UsuarioRequest;
import br.com.tecnodreamer.petconnect.usuario.web.UsuarioResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Optional;

import static br.com.tecnodreamer.petconnect.usuario.UsuarioFixtures.ADMIN;
import static br.com.tecnodreamer.petconnect.usuario.UsuarioFixtures.SERVICO;
import static br.com.tecnodreamer.petconnect.usuario.UsuarioFixtures.VENDAS;
import static br.com.tecnodreamer.petconnect.usuario.UsuarioFixtures.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UsuarioServiceTest {

    @Mock UsuarioRepository usuarios;
    @Mock PapelRepository papeis;
    @Mock FuncionarioRepository funcionarios;
    @Mock AuditoriaService auditoria;

    UsuarioService service;

    @BeforeEach
    void setUp() {
        service = new UsuarioService(usuarios, papeis, funcionarios, new BCryptPasswordEncoder(4), auditoria);
        when(papeis.findByNome("ADMIN")).thenReturn(Optional.of(ADMIN));
        when(papeis.findByNome("VENDAS")).thenReturn(Optional.of(VENDAS));
        when(papeis.findByNome("SERVICO")).thenReturn(Optional.of(SERVICO));
        when(usuarios.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Usuario existente(long id, String username, boolean admin) {
        Usuario u = usuario(id, username, admin ? ADMIN : VENDAS, "hash");
        when(usuarios.travar(id)).thenReturn(Optional.of(u));
        when(usuarios.findById(id)).thenReturn(Optional.of(u));
        return u;
    }

    @Test
    void deveCriarUsuario() {
        UsuarioResponse r = service.criar(new UsuarioRequest(" maria ", "Maria@Empresa.com.br", "Pet@2026Seguro", "VENDAS"));

        assertThat(r.username()).isEqualTo("maria");
        assertThat(r.email()).isEqualTo("maria@empresa.com.br");
        assertThat(r.papel()).isEqualTo("VENDAS");
        assertThat(r.ativo()).isTrue();
        verify(usuarios).saveAndFlush(argThat(u -> new BCryptPasswordEncoder().matches("Pet@2026Seguro", u.getSenhaHash())));
        verify(auditoria).registrar(eq("USUARIO_CRIADO"), eq("usuario"), any(), argThat(d -> !d.toString().contains("Pet@2026")));
    }

    @Test
    void deveRejeitarSenhaForaDaPolitica() {
        assertThatThrownBy(() -> service.criar(new UsuarioRequest("maria", "m@e.com", "fraca", "VENDAS")))
                .isInstanceOf(CampoInvalidoException.class).extracting("campo").isEqualTo("senha");
    }

    @Test
    void deveFalharComUsernameDuplicado() {
        when(usuarios.existsByUsernameIgnoreCase("maria")).thenReturn(true);

        assertThatThrownBy(() -> service.criar(new UsuarioRequest("maria", "m@e.com", "Pet@2026Seguro", "VENDAS")))
                .isInstanceOf(ConflictException.class).hasMessageContaining("nome de usuário");
        verify(usuarios, never()).saveAndFlush(any());
    }

    @Test
    void deveFalharComEmailDuplicado() {
        when(usuarios.existsByEmailIgnoreCase("m@e.com")).thenReturn(true);

        assertThatThrownBy(() -> service.criar(new UsuarioRequest("maria", "M@E.com", "Pet@2026Seguro", "VENDAS")))
                .isInstanceOf(ConflictException.class).hasMessageContaining("e-mail");
    }

    @Test
    void deveAlterarPapelEIncrementarTokenVersion() {
        Usuario u = existente(5, "ana", false);

        service.alterarPapel(5L, "SERVICO", 1L);

        assertThat(u.getPapel().getNome()).isEqualTo("SERVICO");
        assertThat(u.getTokenVersion()).isEqualTo(1);
        verify(auditoria).registrar(eq("USUARIO_PAPEL_ALTERADO"), eq("usuario"), eq(5L),
                argThat(d -> "VENDAS".equals(d.get("papelAnterior")) && "SERVICO".equals(d.get("papelNovo"))));
    }

    @Test
    void deveDesativarUsuarioEIncrementarTokenVersion() {
        Usuario u = existente(5, "ana", false);

        service.desativar(5L, 1L);

        assertThat(u.isAtivo()).isFalse();
        assertThat(u.getTokenVersion()).isEqualTo(1);
        verify(auditoria).registrar(eq("USUARIO_DESATIVADO"), eq("usuario"), eq(5L), anyMap());
    }

    @Test
    void deveAtivarUsuarioEIncrementarTokenVersion() {
        Usuario u = existente(5, "ana", false);
        u.desativar();

        service.ativar(5L);

        assertThat(u.isAtivo()).isTrue();
        assertThat(u.getTokenVersion()).isEqualTo(2);
        verify(auditoria).registrar(eq("USUARIO_ATIVADO"), eq("usuario"), eq(5L), anyMap());
    }

    @Test
    void deveImpedirDesativacaoDoUltimoAdminAtivo() {
        Usuario admin = existente(1, "admin", true);
        when(usuarios.travarAdminsAtivos()).thenReturn(List.of(admin));

        assertThatThrownBy(() -> service.desativar(1L, 99L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Não é possível remover ou desativar o último administrador ativo do sistema.");
        assertThat(admin.isAtivo()).isTrue();
    }

    @Test
    void deveImpedirRebaixamentoDoUltimoAdminAtivo() {
        Usuario admin = existente(1, "admin", true);
        when(usuarios.travarAdminsAtivos()).thenReturn(List.of(admin));

        assertThatThrownBy(() -> service.alterarPapel(1L, "VENDAS", 99L)).isInstanceOf(ConflictException.class);
        assertThat(admin.getPapel().getNome()).isEqualTo("ADMIN");
        assertThat(admin.getTokenVersion()).isZero();
    }

    @Test
    void devePermitirRebaixarAdminQuandoExisteOutroAdminAtivo() {
        Usuario admin = existente(1, "admin", true);
        when(usuarios.travarAdminsAtivos()).thenReturn(List.of(admin, usuario(2, "outro", ADMIN, "h")));

        service.alterarPapel(1L, "VENDAS", 2L);

        assertThat(admin.getPapel().getNome()).isEqualTo("VENDAS");
    }

    @Test
    void naoPermiteAlterarOProprioPapelNemSeDesativar() {
        existente(1, "admin", true);

        assertThatThrownBy(() -> service.alterarPapel(1L, "VENDAS", 1L)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.desativar(1L, 1L)).isInstanceOf(ConflictException.class);
    }
}
