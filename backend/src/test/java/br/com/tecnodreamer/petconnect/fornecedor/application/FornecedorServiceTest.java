package br.com.tecnodreamer.petconnect.fornecedor.application;

import br.com.tecnodreamer.petconnect.compra.domain.StatusCompra;
import br.com.tecnodreamer.petconnect.compra.infrastructure.CompraRepository;
import br.com.tecnodreamer.petconnect.fornecedor.domain.Fornecedor;
import br.com.tecnodreamer.petconnect.fornecedor.infrastructure.FornecedorRepository;
import br.com.tecnodreamer.petconnect.fornecedor.web.FornecedorRequest;
import br.com.tecnodreamer.petconnect.fornecedor.web.FornecedorResponse;
import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;
import br.com.tecnodreamer.petconnect.shared.error.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static br.com.tecnodreamer.petconnect.produto.EstoqueFixtures.CNPJ;
import static br.com.tecnodreamer.petconnect.produto.EstoqueFixtures.ENDERECO;
import static br.com.tecnodreamer.petconnect.produto.EstoqueFixtures.fornecedor;
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
class FornecedorServiceTest {

    @Mock FornecedorRepository fornecedores;
    @Mock CompraRepository compras;
    @Mock AuditoriaService auditoria;

    FornecedorService service;

    @BeforeEach
    void setUp() {
        service = new FornecedorService(fornecedores, compras, auditoria);
        when(fornecedores.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static FornecedorRequest req(String cnpj, String telefone) {
        return new FornecedorRequest(" Pet Distribuidora ", "Pet Distribuidora Ltda", cnpj, telefone,
                "contato@pet.com.br", "Carlos", ENDERECO, null);
    }

    private Fornecedor existente(boolean ativo) {
        Fornecedor f = fornecedor(4, ativo);
        f.definirEndereco(ENDERECO.criar());
        when(fornecedores.findWithEnderecoById(4L)).thenReturn(Optional.of(f));
        return f;
    }

    @Test
    void deveCriarFornecedorComCnpjValido() {
        FornecedorResponse r = service.criar(req("11.222.333/0001-81", "(19) 3500-0000"));

        assertThat(r.cnpj()).isEqualTo(CNPJ);
        assertThat(r.nomeFantasia()).isEqualTo("Pet Distribuidora");
        assertThat(r.telefone()).isEqualTo("1935000000");
        assertThat(r.endereco().cidade()).isEqualTo("Leme");
        verify(auditoria).registrar(eq("FORNECEDOR_CRIADO"), eq("fornecedor"), any(), anyMap());
    }

    @Test
    void deveRejeitarCnpjInvalido() {
        assertThatThrownBy(() -> service.criar(req("11.222.333/0001-80", null)))
                .isInstanceOf(CampoInvalidoException.class).extracting("campo").isEqualTo("cnpj");
    }

    @Test
    void deveRejeitarCnpjDuplicado() {
        when(fornecedores.existsByCnpj(CNPJ)).thenReturn(true);

        assertThatThrownBy(() -> service.criar(req(CNPJ, null))).isInstanceOf(ConflictException.class);
        verify(fornecedores, never()).saveAndFlush(any());
    }

    @Test
    void deveRejeitarTelefoneDuplicado() {
        when(fornecedores.existsByTelefone("1935000000")).thenReturn(true);

        assertThatThrownBy(() -> service.criar(req(CNPJ, "1935000000"))).isInstanceOf(ConflictException.class)
                .hasMessageContaining("telefone");
    }

    @Test
    void deveAtualizarFornecedor() {
        Fornecedor f = existente(true);

        service.atualizar(4L, new FornecedorRequest("Novo Nome", "Nova Razão", CNPJ, null, null, null,
                new br.com.tecnodreamer.petconnect.endereco.web.EnderecoRequest("13610-000", "Rua Nova", "1", null, null, "Araras", "SP"), null));

        assertThat(f.getNomeFantasia()).isEqualTo("Novo Nome");
        assertThat(f.getEndereco().getCidade()).isEqualTo("Araras");
        verify(auditoria).registrar(eq("FORNECEDOR_ATUALIZADO"), eq("fornecedor"), eq(4L), anyMap());
    }

    @Test
    void deveDesativarFornecedorSemCompraAberta() {
        Fornecedor f = existente(true);

        service.desativar(4L);

        assertThat(f.isAtivo()).isFalse();
        verify(auditoria).registrar(eq("FORNECEDOR_DESATIVADO"), eq("fornecedor"), eq(4L), anyMap());
    }

    @Test
    void deveImpedirDesativacaoComCompraPedido() {
        Fornecedor f = existente(true);
        when(compras.existsByFornecedorIdAndStatus(4L, StatusCompra.PEDIDO)).thenReturn(true);

        assertThatThrownBy(() -> service.desativar(4L)).isInstanceOf(ConflictException.class)
                .hasMessage("O fornecedor possui compras em aberto e não pode ser desativado.");
        assertThat(f.isAtivo()).isTrue();
    }

    @Test
    void deveReativarFornecedor() {
        Fornecedor f = existente(false);

        service.ativar(4L);

        assertThat(f.isAtivo()).isTrue();
        verify(auditoria).registrar(eq("FORNECEDOR_ATIVADO"), eq("fornecedor"), eq(4L), anyMap());
    }
}
