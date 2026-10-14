package br.com.tecnodreamer.petconnect.produto.web;

import java.time.LocalDate;
import java.util.List;

/**
 * Alertas de estoque derivados do dado real a cada consulta (nada é gravado nem disparado, então não há notificação
 * duplicada). {@code habilitado} diz se o controle de lote está ligado nesta instalação: desligado, a tela esconde os alertas.
 */
public record AlertasEstoqueResponse(boolean habilitado, LocalDate hoje, List<Faixa> faixas, Grupo vencidos, Grupo bloqueados,
        long produtosSemControle, long produtosAbaixoDoMinimo) {

    /** Lotes com saldo cuja validade cai entre hoje+{@code deDias} e hoje+{@code ateDias}, inclusive. */
    public record Faixa(int deDias, int ateDias, long lotes, long unidades) {
    }

    public record Grupo(long lotes, long unidades) {
    }
}
