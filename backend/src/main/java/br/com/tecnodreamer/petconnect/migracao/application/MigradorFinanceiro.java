package br.com.tecnodreamer.petconnect.migracao.application;

import br.com.tecnodreamer.petconnect.financeiro.application.FinanceiroService;
import br.com.tecnodreamer.petconnect.financeiro.domain.CategoriaLancamento;
import br.com.tecnodreamer.petconnect.financeiro.domain.Lancamento;
import br.com.tecnodreamer.petconnect.financeiro.domain.TipoLancamento;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Financeiro legado (DA-29, DA-30). Venda e serviço são IGNORADOS: o backfill os
 * regenera a partir das vendas/serviços migrados (sem duplicar). O resto entra pelo
 * FinanceiroService com chave {@code LEGADO:<id>}.
 */
@Component
@Order(220)
public class MigradorFinanceiro implements Migrador {

    static final String OBSERVACAO = "Migrado do sistema antigo";

    private final FinanceiroService financeiro;

    public MigradorFinanceiro(FinanceiroService financeiro) {
        this.financeiro = financeiro;
    }

    @Override
    public String entidade() {
        return "movimentacao_financeira";
    }

    @Override
    public String consulta() {
        return """
                SELECT id, tipo, descricao, valor, data_movimentacao, categoria, observacoes
                  FROM movimentacao_financeira WHERE id > :ultimo ORDER BY id LIMIT :lote
                """;
    }

    @Override
    public Desfecho migrar(Linha l, Contexto ctx) {
        Optional<TipoLancamento> tipo = ConversorLegado.tipoFinanceiro(l.texto("tipo"));
        if (tipo.isEmpty()) {
            return Desfecho.ignorado("TIPO_FINANCEIRO_DESCONHECIDO");
        }
        List<String> pend = new ArrayList<>();
        CategoriaLancamento categoria = ConversorLegado.categoriaFinanceira(l.texto("categoria")).anotar(pend);
        if (categoria == CategoriaLancamento.VENDA_PRODUTO || categoria == CategoriaLancamento.SERVICO) {
            return Desfecho.ignorado("REGENERADO_PELO_BACKFILL");
        }
        BigDecimal valor = l.decimal("valor");
        if (valor == null || valor.signum() <= 0) {
            return Desfecho.ignorado("VALOR_INVALIDO");
        }
        LocalDate data = l.data("data_movimentacao");
        if (data == null) {
            return Desfecho.ignorado("DADOS_OBRIGATORIOS_AUSENTES");
        }
        String descricao = l.texto("descricao") == null ? "Lançamento do sistema antigo" : l.texto("descricao");
        String obs = l.texto("observacoes") == null ? OBSERVACAO : OBSERVACAO + "\n" + l.texto("observacoes");
        Lancamento lanc = financeiro.registrarLegado(tipo.get(), categoria, descricao, valor, data, l.id(), obs);
        return Desfecho.migrado(lanc.getId(), pend);
    }
}
