package br.com.tecnodreamer.petconnect.produto;

import br.com.tecnodreamer.petconnect.compra.domain.Compra;
import br.com.tecnodreamer.petconnect.endereco.web.EnderecoRequest;
import br.com.tecnodreamer.petconnect.fornecedor.domain.Fornecedor;
import br.com.tecnodreamer.petconnect.produto.domain.CategoriaProduto;
import br.com.tecnodreamer.petconnect.produto.domain.Produto;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class EstoqueFixtures {

    /** CNPJ válido conhecido. */
    public static final String CNPJ = "11222333000181";
    public static final EnderecoRequest ENDERECO = new EnderecoRequest("13610-000", "Rua Exemplo", "500", null, "Centro", "Leme", "SP");

    private EstoqueFixtures() {
    }

    public static CategoriaProduto categoria(long id, String nome) {
        CategoriaProduto c = BeanUtils.instantiateClass(CategoriaProduto.class);
        ReflectionTestUtils.setField(c, "id", id);
        ReflectionTestUtils.setField(c, "nome", nome);
        return c;
    }

    public static Fornecedor fornecedor(long id, boolean ativo) {
        Fornecedor f = new Fornecedor("Pet Distribuidora", "Pet Distribuidora Ltda", CNPJ, null, null, null, null);
        ReflectionTestUtils.setField(f, "id", id);
        if (!ativo) {
            f.desativar();
        }
        return f;
    }

    /** Produto com saldo e custo definidos "por fora" (como se viessem do banco). */
    public static Produto produto(long id, int saldo, String custo, int minimo) {
        Produto p = new Produto("Produto " + id, null, categoria(1, "Ração"), null, null,
                custo == null ? null : new BigDecimal(custo), new BigDecimal("99.90"), minimo);
        ReflectionTestUtils.setField(p, "id", id);
        ReflectionTestUtils.setField(p, "quantidadeEstoque", saldo);
        return p;
    }

    public static Compra compra(long id, Fornecedor fornecedor, LocalDate data) {
        Compra c = new Compra(fornecedor, null, data, "BOLETO", "Obs original");
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }
}
