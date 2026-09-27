package br.com.tecnodreamer.petconnect.servico.application;

import br.com.tecnodreamer.petconnect.pet.domain.Porte;
import br.com.tecnodreamer.petconnect.servico.domain.Servico;
import br.com.tecnodreamer.petconnect.servico.infrastructure.ServicoRepository;
import br.com.tecnodreamer.petconnect.servico.web.PrecoPorteRequest;
import br.com.tecnodreamer.petconnect.servico.web.ServicoRequest;
import br.com.tecnodreamer.petconnect.servico.web.ServicoResponse;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static br.com.tecnodreamer.petconnect.agendamento.AgendaFixtures.banho;
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
class ServicoServiceTest {

    @Mock ServicoRepository servicos;
    @Mock AuditoriaService auditoria;

    ServicoService service;

    @BeforeEach
    void setUp() {
        service = new ServicoService(servicos, auditoria);
        when(servicos.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static PrecoPorteRequest preco(Porte porte, String valor) {
        return new PrecoPorteRequest(porte, new BigDecimal(valor));
    }

    @Test
    void deveCriarServico() {
        ServicoResponse r = service.criar(new ServicoRequest(" Tosa Higiênica ", "Áreas específicas", new BigDecimal("50.00"), 45,
                List.of(preco(Porte.PEQUENO, "45.00"), preco(Porte.GRANDE, "65.00"))));

        assertThat(r.nome()).isEqualTo("Tosa Higiênica");
        assertThat(r.ativo()).isTrue();
        assertThat(r.precosPorPorte()).extracting(ServicoResponse.PrecoPorte::porte).containsExactly(Porte.PEQUENO, Porte.GRANDE);
        verify(auditoria).registrar(eq("SERVICO_CRIADO"), eq("servico"), any(), anyMap());
    }

    @Test
    void deveFalharComNomeDuplicado() {
        when(servicos.existsByNomeIgnoreCase("Banho")).thenReturn(true);

        assertThatThrownBy(() -> service.criar(new ServicoRequest("Banho", null, BigDecimal.TEN, 30, null)))
                .isInstanceOf(ConflictException.class);
        verify(servicos, never()).saveAndFlush(any());
    }

    @Test
    void deveRecusarDoisPrecosParaOMesmoPorte() {
        assertThatThrownBy(() -> service.criar(new ServicoRequest("Novo", null, BigDecimal.TEN, 30,
                List.of(preco(Porte.MEDIO, "1"), preco(Porte.MEDIO, "2")))))
                .isInstanceOf(CampoInvalidoException.class).extracting("campo").isEqualTo("precosPorPorte");
    }

    @Test
    void deveAtualizarServicoEPrecosPorPorte() {
        Servico banho = banho();
        when(servicos.findById(1L)).thenReturn(Optional.of(banho));

        service.atualizar(1L, new ServicoRequest("Banho", null, new BigDecimal("52.00"), 50,
                List.of(preco(Porte.MEDIO, "58.00"), preco(Porte.GRANDE, "70.00"), preco(Porte.PEQUENO, "48.00"))));

        assertThat(banho.getPreco()).isEqualByComparingTo("52.00");
        assertThat(banho.getDuracaoMinutos()).isEqualTo(50);
        assertThat(banho.precoPara(Porte.MEDIO)).contains(new BigDecimal("58.00"));
        assertThat(banho.precoPara(Porte.PEQUENO)).contains(new BigDecimal("48.00"));
        verify(auditoria).registrar(eq("SERVICO_ATUALIZADO"), eq("servico"), eq(1L), anyMap());
    }

    @Test
    void deveRemoverPrecoPorPorteOmitidoNaAtualizacao() {
        Servico banho = banho();
        when(servicos.findById(1L)).thenReturn(Optional.of(banho));

        service.atualizar(1L, new ServicoRequest("Banho", null, new BigDecimal("50.00"), 60,
                List.of(preco(Porte.MEDIO, "55.00"))));

        assertThat(banho.getPrecos()).hasSize(1);
        assertThat(banho.precoPara(Porte.GRANDE)).isEmpty();
    }

    @Test
    void deveDesativarServico() {
        Servico banho = banho();
        when(servicos.findById(1L)).thenReturn(Optional.of(banho));

        service.desativar(1L);

        assertThat(banho.isAtivo()).isFalse();
        verify(auditoria).registrar(eq("SERVICO_DESATIVADO"), eq("servico"), eq(1L), anyMap());
    }

    @Test
    void reconheceVacinacaoSomentePeloNomeExato() {
        assertThat(new Servico(" vacinação ", null, BigDecimal.ONE, 15, Map.of()).isVacinacao()).isTrue();
        assertThat(new Servico("Vacinação antirrábica", null, BigDecimal.ONE, 15, Map.of()).isVacinacao()).isFalse();
        assertThat(new Servico("Vacinacao", null, BigDecimal.ONE, 15, Map.of()).isVacinacao()).isFalse();
    }
}
