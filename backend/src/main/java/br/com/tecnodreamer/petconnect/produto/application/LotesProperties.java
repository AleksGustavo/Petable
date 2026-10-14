package br.com.tecnodreamer.petconnect.produto.application;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code petconnect.estoque.lotes}: liberação do controle de lote (D22) e margem global de bloqueio (D4).
 * {@code habilitado} nasce falso: desligado, o sistema se comporta exatamente como antes dos lotes.
 */
@Validated
@ConfigurationProperties("petconnect.estoque.lotes")
public record LotesProperties(
        @DefaultValue("false") boolean habilitado,
        @Min(0) @DefaultValue("0") int diasBloqueioAntesValidade) {

    /** Margem efetiva do produto: a própria, se definida; senão a global. */
    public int margemDoProduto(Integer diasBloqueioDoProduto) {
        return diasBloqueioDoProduto != null ? diasBloqueioDoProduto : diasBloqueioAntesValidade;
    }
}
