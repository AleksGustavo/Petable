package br.com.tecnodreamer.petconnect.cliente.application;

import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;
import br.com.tecnodreamer.petconnect.shared.error.ConflictException;
import br.com.tecnodreamer.petconnect.shared.error.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientePrivacidadeServiceTest {

    @Mock NamedParameterJdbcTemplate jdbc;
    @Mock AuditoriaService auditoria;

    ClientePrivacidadeService service;

    @BeforeEach
    void setUp() {
        service = new ClientePrivacidadeService(jdbc, auditoria);
    }

    private void cliente(String nome, Long enderecoId) {
        Map<String, Object> c = new HashMap<>();
        c.put("id", 7L);
        c.put("nome", nome);
        c.put("endereco_id", enderecoId);
        when(jdbc.queryForList(contains("FROM cliente"), anyMap())).thenReturn(List.of(c));
    }

    @Test
    void deveAnonimizarDadosDoCliente() {
        cliente("Maria Exemplo", 3L);

        service.anonimizar(7L, "Pedido do titular por e-mail");

        ArgumentCaptor<Map<String, Object>> p = ArgumentCaptor.captor();
        verify(jdbc).update(contains("UPDATE cliente"), p.capture());
        assertThat(p.getValue()).containsEntry("nome", "Cliente anonimizado #7").containsEntry("telefone", "00000000007");
        verify(jdbc).update(contains("UPDATE endereco"), eq(Map.of("id", 3L)));
        verify(jdbc).update(contains("UPDATE pet SET observacoes = NULL"), eq(Map.of("id", 7L)));
    }

    @Test
    void telefoneMarcadorTemOnzeDigitosComDddZero() {
        assertThat(ClientePrivacidadeService.telefoneMarcador(123L)).isEqualTo("00000000123").hasSize(11);
    }

    @Test
    void semEnderecoNaoAtualizaEndereco() {
        cliente("Maria Exemplo", null);

        service.anonimizar(7L, "Pedido do titular");

        verify(jdbc, never()).update(contains("UPDATE endereco"), anyMap());
    }

    @Test
    void jaAnonimizadoDeveDar409() {
        cliente("Cliente anonimizado #7", null);

        assertThatThrownBy(() -> service.anonimizar(7L, "De novo, por engano")).isInstanceOf(ConflictException.class);
    }

    @Test
    void motivoCurtoDeveDar400() {
        cliente("Maria", null);

        assertThatThrownBy(() -> service.anonimizar(7L, "ok")).isInstanceOf(CampoInvalidoException.class);
        verify(jdbc, never()).update(anyString(), anyMap());
    }

    @Test
    void clienteInexistenteDeveDar404() {
        when(jdbc.queryForList(contains("FROM cliente"), anyMap())).thenReturn(List.of());

        assertThatThrownBy(() -> service.anonimizar(99L, "Pedido do titular")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void auditoriaNaoLevaDadosPessoais() {
        cliente("Maria Exemplo", null);

        service.anonimizar(7L, "Pedido do titular");

        verify(auditoria).registrar("CLIENTE_ANONIMIZADO", "cliente", 7L, Map.of("motivo", "Pedido do titular"));
    }
}
