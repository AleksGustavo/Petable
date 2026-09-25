package br.com.tecnodreamer.petconnect.cliente.application;

import br.com.tecnodreamer.petconnect.cliente.domain.Cliente;
import br.com.tecnodreamer.petconnect.cliente.infrastructure.ClienteRepository;
import br.com.tecnodreamer.petconnect.cliente.web.ClienteRequest;
import br.com.tecnodreamer.petconnect.endereco.web.EnderecoRequest;
import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.error.ConflictException;
import br.com.tecnodreamer.petconnect.shared.error.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    @Mock
    ClienteRepository repository;

    @Mock
    AuditoriaService auditoria;

    @InjectMocks
    ClienteService service;

    private static ClienteRequest request(String cpf, String telefone) {
        return new ClienteRequest("  Maria Silva ", cpf, null, telefone, " ",
                new EnderecoRequest("13616-140", "Rua Evaristo", "10", null, "Centro", "Leme", "sp"), null);
    }

    @Test
    void criarNormalizaDocumentosEEndereco() {
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service.criar(request("529.982.247-25", "(19) 99876-5432"));

        ArgumentCaptor<Cliente> captor = ArgumentCaptor.forClass(Cliente.class);
        verify(repository).saveAndFlush(captor.capture());
        Cliente salvo = captor.getValue();
        assertThat(salvo.getNome()).isEqualTo("Maria Silva");
        assertThat(salvo.getCpf()).isEqualTo("52998224725");
        assertThat(salvo.getTelefone()).isEqualTo("19998765432");
        assertThat(salvo.getEmail()).isNull();
        assertThat(salvo.getEndereco().getCep()).isEqualTo("13616140");
        assertThat(salvo.getEndereco().getUf()).isEqualTo("SP");
        verify(auditoria).registrar(eq("CRIAR"), eq("cliente"), any(), anyMap());
    }

    @Test
    void criarRecusaCpfDuplicado() {
        when(repository.existsByCpf("52998224725")).thenReturn(true);

        assertThatThrownBy(() -> service.criar(request("529.982.247-25", "19998765432")))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void criarRecusaTelefoneDuplicado() {
        when(repository.existsByTelefone("19998765432")).thenReturn(true);

        assertThatThrownBy(() -> service.criar(request(null, "19998765432")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void buscarClienteInativoResponde404() {
        Cliente inativo = new Cliente("Ana", null, null, "19998765432", null, null);
        inativo.inativar();
        when(repository.findWithEnderecoById(7L)).thenReturn(Optional.of(inativo));

        assertThatThrownBy(() -> service.buscar(7L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void atualizarSemEnderecoRemoveOEnderecoAtual() {
        Cliente existente = new Cliente("Ana", null, null, "19998765432", null, null);
        existente.definirEndereco(request(null, "19998765432").endereco().criar());
        when(repository.findWithEnderecoById(1L)).thenReturn(Optional.of(existente));
        when(repository.saveAndFlush(existente)).thenReturn(existente);

        service.atualizar(1L, new ClienteRequest("Ana", null, null, "19998765432", null, null, null));

        assertThat(existente.getEndereco()).isNull();
    }
}
