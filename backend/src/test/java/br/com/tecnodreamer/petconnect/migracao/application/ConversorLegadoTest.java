package br.com.tecnodreamer.petconnect.migracao.application;

import br.com.tecnodreamer.petconnect.agendamento.domain.StatusAgendamento;
import br.com.tecnodreamer.petconnect.financeiro.domain.CategoriaLancamento;
import br.com.tecnodreamer.petconnect.financeiro.domain.TipoLancamento;
import br.com.tecnodreamer.petconnect.pet.domain.Porte;
import br.com.tecnodreamer.petconnect.venda.domain.FormaPagamento;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConversorLegadoTest {

    @Test
    void deveConverterFormasPagamentoConhecidas() {
        assertThat(ConversorLegado.formaPagamento("cartao_credito").valor()).isEqualTo(FormaPagamento.CARTAO_CREDITO);
        assertThat(ConversorLegado.formaPagamento("Cartão Débito").valor()).isEqualTo(FormaPagamento.CARTAO_DEBITO);
        assertThat(ConversorLegado.formaPagamento("pix").pendencia()).isNull();
        assertThat(ConversorLegado.formaPagamento("transferência").valor()).isEqualTo(FormaPagamento.TRANSFERENCIA);
    }

    @Test
    void deveConverterStatusAgendamentoComAcento() {
        assertThat(ConversorLegado.statusAgendamento("Concluído").valor()).isEqualTo(StatusAgendamento.CONCLUIDO);
        assertThat(ConversorLegado.statusAgendamento("não compareceu").valor()).isEqualTo(StatusAgendamento.NAO_COMPARECEU);
        assertThat(ConversorLegado.statusAgendamento("em_andamento").valor()).isEqualTo(StatusAgendamento.EM_ANDAMENTO);
    }

    @Test
    void deveConverterPorteMedio() {
        assertThat(ConversorLegado.porte("Medio").valor()).isEqualTo(Porte.MEDIO);
        assertThat(ConversorLegado.porte("médio").valor()).isEqualTo(Porte.MEDIO);
        assertThat(ConversorLegado.porte(null).valor()).isNull();
    }

    @Test
    void deveGerarPendenciaParaEnumDesconhecido() {
        assertThat(ConversorLegado.porte("Enorme")).isEqualTo(Conversao.comPendencia(Porte.MEDIO, "PORTE_DESCONHECIDO"));
        assertThat(ConversorLegado.statusAgendamento("desmarcado").pendencia()).isEqualTo("STATUS_AGENDAMENTO_DESCONHECIDO");
        assertThat(ConversorLegado.statusAgendamento("desmarcado").valor()).isEqualTo(StatusAgendamento.CANCELADO);
        assertThat(ConversorLegado.formaPagamento(null).pendencia()).isEqualTo("FORMA_PAGAMENTO_DESCONHECIDA");
        assertThat(ConversorLegado.sexo("x").pendencia()).isEqualTo("SEXO_DESCONHECIDO");
        assertThat(ConversorLegado.categoriaFinanceira("brinde").valor()).isEqualTo(CategoriaLancamento.OUTROS);
        assertThat(ConversorLegado.tipoFinanceiro("transferencia")).isEmpty();
        assertThat(ConversorLegado.tipoFinanceiro("saída")).contains(TipoLancamento.SAIDA);
        assertThat(ConversorLegado.papel("Estagiario").pendencia()).isEqualTo("PAPEL_DESCONHECIDO");
    }

    @Test
    void deveConverterBooleanosLegados() {
        assertThat(ConversorLegado.booleano(1, false).valor()).isTrue();
        assertThat(ConversorLegado.booleano("Sim", false).valor()).isTrue();
        assertThat(ConversorLegado.booleano("não", true).valor()).isFalse();
        assertThat(ConversorLegado.booleano(null, true).valor()).isTrue();
        assertThat(ConversorLegado.booleano("talvez", true)).isEqualTo(Conversao.comPendencia(false, "BOOLEANO_DESCONHECIDO"));
    }
}
