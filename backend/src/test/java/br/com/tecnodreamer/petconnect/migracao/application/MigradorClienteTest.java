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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MigradorClienteTest {

    static final String CPF_VALIDO = "529.982.247-25";

    @Mock DestinoMigracao destino;
    @Mock ConsultaCep cep;

    MigradorCliente migrador;
    ContextoFake ctx = new ContextoFake();

    @BeforeEach
    void setUp() {
        migrador = new MigradorCliente(destino, new EnderecoLegado(destino, cep));
        when(destino.inserir(eq("endereco"), anyMap())).thenReturn(50L);
        when(destino.inserir(eq("cliente"), anyMap())).thenReturn(10L);
    }

    Linha cliente(String cpf, String cep, String rua, String estado) {
        return Linha.of("id", 3, "nome", "Cliente Teste", "cpf", cpf, "telefone", "(11) 98888-7777",
                "email", "cliente@exemplo.test", "cep", cep, "rua", rua, "bairro", cep == null ? null : "Centro",
                "numero", cep == null ? null : "10",
                "estado", estado, "ativo", 1);
    }

    Map<String, Object> gravado(String tabela) {
        ArgumentCaptor<Map<String, Object>> c = ArgumentCaptor.captor();
        verify(destino).inserir(eq(tabela), c.capture());
        return c.getValue();
    }

    @Test
    void deveExtrairEndereco() {
        Desfecho d = migrador.migrar(cliente(CPF_VALIDO, "01001-000", "Praça da Sé", "São Paulo"), ctx);

        Map<String, Object> e = gravado("endereco");
        assertThat(e).containsEntry("cep", "01001000").containsEntry("logradouro", "Praça da Sé")
                .containsEntry("uf", "SP").containsEntry("cidade", "Não informada");
        assertThat(gravado("cliente")).containsEntry("endereco_id", 50L).containsEntry("telefone", "11988887777");
        assertThat(d.pendencias()).containsExactly("ENDERECO_CIDADE_NAO_INFORMADA");
    }

    @Test
    void deveLimparMascaraCpf() {
        migrador.migrar(cliente(CPF_VALIDO, null, null, null), ctx);

        assertThat(gravado("cliente")).containsEntry("cpf", "52998224725");
    }

    @Test
    void cpfInvalidoDeveVirarNullEPendencia() {
        Desfecho d = migrador.migrar(cliente("123.456.789-00", null, null, null), ctx);

        Map<String, Object> v = gravado("cliente");
        assertThat(v.get("cpf")).isNull();
        assertThat(v.get("observacoes")).isEqualTo(MigradorCliente.OBS_CPF_INVALIDO);
        assertThat(d.situacao()).isEqualTo(Situacao.AJUSTADO);
        assertThat(d.pendencias()).containsExactly("CPF_INVALIDO");
    }

    @Test
    void cpfDuplicadoDeveVirarNullNoSegundo() {
        when(destino.cpfClienteExiste("52998224725")).thenReturn(true);

        Desfecho d = migrador.migrar(cliente(CPF_VALIDO, null, null, null), ctx);

        assertThat(gravado("cliente").get("cpf")).isNull();
        assertThat(d.pendencias()).containsExactly("CPF_DUPLICADO");
    }

    @Test
    void falhaViaCepNaoDeveAbortar() {
        when(cep.consultar(anyString())).thenThrow(new IllegalStateException("timeout"));

        Desfecho d = migrador.migrar(cliente(CPF_VALIDO, "01001-000", "Praça da Sé", "SP"), ctx.viaCep(true));

        assertThat(d.idNovo()).isEqualTo(10L);
        assertThat(d.pendencias()).contains("VIACEP_INDISPONIVEL", "ENDERECO_CIDADE_NAO_INFORMADA");
    }

    @Test
    void telefoneObrigatorioInvalidoDeveIgnorarCliente() {
        Desfecho d = migrador.migrar(Linha.of("id", 4, "nome", "X", "telefone", "123"), ctx);

        assertThat(d.situacao()).isEqualTo(Situacao.IGNORADO);
        verify(destino, never()).inserir(eq("cliente"), any());
    }
}
