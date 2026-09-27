package br.com.tecnodreamer.petconnect.funcionario.application;

import br.com.tecnodreamer.petconnect.funcionario.domain.Funcionario;
import br.com.tecnodreamer.petconnect.funcionario.domain.Sexo;
import br.com.tecnodreamer.petconnect.funcionario.infrastructure.FuncionarioRepository;
import br.com.tecnodreamer.petconnect.funcionario.web.FuncionarioRequest;
import br.com.tecnodreamer.petconnect.funcionario.web.FuncionarioResponse;
import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;
import br.com.tecnodreamer.petconnect.shared.error.ConflictException;
import br.com.tecnodreamer.petconnect.usuario.domain.Usuario;
import br.com.tecnodreamer.petconnect.usuario.infrastructure.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static br.com.tecnodreamer.petconnect.pet.PetFixtures.CLOCK;
import static br.com.tecnodreamer.petconnect.pet.PetFixtures.HOJE;
import static br.com.tecnodreamer.petconnect.usuario.UsuarioFixtures.VENDAS;
import static br.com.tecnodreamer.petconnect.usuario.UsuarioFixtures.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuncionarioServiceTest {

    @Mock FuncionarioRepository funcionarios;
    @Mock UsuarioRepository usuarios;
    @Mock AuditoriaService auditoria;

    FuncionarioService service;
    final Usuario ana = usuario(10, "ana", VENDAS, "h");

    @BeforeEach
    void setUp() {
        service = new FuncionarioService(funcionarios, usuarios, auditoria, CLOCK);
        when(usuarios.findById(10L)).thenReturn(Optional.of(ana));
        when(funcionarios.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static FuncionarioRequest req(String cpf, String telefone, LocalDate nascimento) {
        return new FuncionarioRequest(10L, cpf, " Ana Souza ", nascimento, Sexo.FEMININO, telefone, null, null);
    }

    @Test
    void deveCriarFuncionarioVinculadoAUsuario() {
        FuncionarioResponse r = service.criar(req("529.982.247-25", "(19) 99999-0000", LocalDate.of(1995, 4, 20)));

        assertThat(r.usuarioId()).isEqualTo(10L);
        assertThat(r.nome()).isEqualTo("Ana Souza");
        assertThat(r.cpf()).isEqualTo("52998224725");
        assertThat(r.telefone()).isEqualTo("19999990000");
        assertThat(r.disponivel()).isTrue();
        verify(auditoria).registrar(eq("FUNCIONARIO_CRIADO"), eq("funcionario"), any(), anyMap());
    }

    @Test
    void deveImpedirSegundoFuncionarioParaMesmoUsuario() {
        when(funcionarios.existsByUsuarioId(10L)).thenReturn(true);

        assertThatThrownBy(() -> service.criar(req("52998224725", null, null))).isInstanceOf(ConflictException.class);
        verify(funcionarios, never()).saveAndFlush(any());
    }

    @Test
    void deveRejeitarCpfInvalido() {
        assertThatThrownBy(() -> service.criar(req("123.456.789-00", null, null)))
                .isInstanceOf(CampoInvalidoException.class).extracting("campo").isEqualTo("cpf");
    }

    @Test
    void deveRejeitarCpfDuplicado() {
        when(funcionarios.existsByCpf("52998224725")).thenReturn(true);

        assertThatThrownBy(() -> service.criar(req("52998224725", null, null))).isInstanceOf(ConflictException.class)
                .hasMessageContaining("CPF");
    }

    @Test
    void deveRejeitarTelefoneDuplicado() {
        when(funcionarios.existsByTelefone("19999990000")).thenReturn(true);

        assertThatThrownBy(() -> service.criar(req("52998224725", "19999990000", null))).isInstanceOf(ConflictException.class)
                .hasMessageContaining("telefone");
    }

    @Test
    void deveRejeitarNascimentoFuturo() {
        assertThatThrownBy(() -> service.criar(req("52998224725", null, HOJE.plusDays(1))))
                .isInstanceOf(CampoInvalidoException.class).extracting("campo").isEqualTo("dataNascimento");
    }

    @Test
    void deveListarSomenteFuncionariosComUsuarioAtivo() {
        Funcionario f = new Funcionario(ana, "52998224725");
        f.atualizarDados("Ana", null, null, null, null);
        when(funcionarios.disponiveis(null)).thenReturn(List.of(f));

        assertThat(service.listarDisponiveis(" ")).extracting("nome").containsExactly("Ana");
        verify(funcionarios).disponiveis(null);
        ana.desativar();
        assertThat(f.isDisponivel()).isFalse();
    }
}
