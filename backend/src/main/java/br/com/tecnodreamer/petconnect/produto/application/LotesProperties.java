package br.com.tecnodreamer.petconnect.produto.application;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * {@code petconnect.estoque.lotes}: liberação do controle de lote (D22), margem global de bloqueio (D4) e faixas dos
 * alertas de vencimento em dias (D23, padrão 30/60/90). {@code habilitado} nasce falso: desligado, o sistema se comporta
 * exatamente como antes dos lotes.
 */
@Validated
@ConfigurationProperties("petconnect.estoque.lotes")
public record LotesProperties(
        @DefaultValue("false") boolean habilitado,
        @Min(0) @DefaultValue("0") int diasBloqueioAntesValidade,
        @DefaultValue({"30", "60", "90"}) List<Integer> faixasAlertaDias) {

    @ConstructorBinding
    public LotesProperties {
        List<Integer> faixas = faixasAlertaDias == null || faixasAlertaDias.isEmpty() ? List.of(30, 60, 90) : faixasAlertaDias;
        int anterior = 0;
        for (Integer f : faixas) {
            if (f == null || f <= anterior) {
                throw new IllegalArgumentException("As faixas de alerta devem ser dias positivos em ordem crescente.");
            }
            anterior = f;
        }
        faixasAlertaDias = List.copyOf(faixas);
    }

    public LotesProperties(boolean habilitado, int diasBloqueioAntesValidade) {
        this(habilitado, diasBloqueioAntesValidade, List.of(30, 60, 90));
    }

    /** Margem efetiva do produto: a própria, se definida; senão a global. */
    public int margemDoProduto(Integer diasBloqueioDoProduto) {
        return diasBloqueioDoProduto != null ? diasBloqueioDoProduto : diasBloqueioAntesValidade;
    }
}
