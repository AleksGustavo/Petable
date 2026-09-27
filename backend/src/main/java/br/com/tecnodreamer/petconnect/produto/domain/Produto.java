package br.com.tecnodreamer.petconnect.produto.domain;

import br.com.tecnodreamer.petconnect.fornecedor.domain.Fornecedor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Produto do catálogo. O saldo ({@code quantidade_estoque}) nunca é editado
 * pelo cadastro: só muda por {@link #movimentar(int)}, chamado pelo serviço
 * central de estoque com o produto travado e sempre gerando histórico.
 */
@Entity
@Table(name = "produto")
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;
    private String descricao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id")
    private CategoriaProduto categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fornecedor_padrao_id")
    private Fornecedor fornecedorPadrao;

    @Column(name = "codigo_barras")
    private String codigoBarras;

    @Column(name = "preco_custo")
    private BigDecimal precoCusto;

    @Column(name = "preco_venda")
    private BigDecimal precoVenda;

    @Column(name = "quantidade_estoque")
    private int quantidadeEstoque;

    @Column(name = "estoque_minimo")
    private int estoqueMinimo;

    private boolean ativo = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Produto() {
    }

    /** Nasce ativo e com saldo zero (DA-02). O custo informado é só referência inicial (DA-04). */
    public Produto(String nome, String descricao, CategoriaProduto categoria, Fornecedor fornecedorPadrao,
            String codigoBarras, BigDecimal precoCusto, BigDecimal precoVenda, int estoqueMinimo) {
        atualizar(nome, descricao, categoria, fornecedorPadrao, codigoBarras, precoVenda, estoqueMinimo);
        this.precoCusto = precoCusto;
        this.quantidadeEstoque = 0;
    }

    /** Dados editáveis: não inclui saldo, custo nem ativo. */
    public final void atualizar(String nome, String descricao, CategoriaProduto categoria, Fornecedor fornecedorPadrao,
            String codigoBarras, BigDecimal precoVenda, int estoqueMinimo) {
        this.nome = nome;
        this.descricao = descricao;
        this.categoria = categoria;
        this.fornecedorPadrao = fornecedorPadrao;
        this.codigoBarras = codigoBarras;
        this.precoVenda = precoVenda;
        this.estoqueMinimo = estoqueMinimo;
    }

    /** Aplica um delta ao saldo e devolve o saldo anterior. Nunca deixa negativo. */
    public int movimentar(int delta) {
        int anterior = quantidadeEstoque;
        if (anterior + delta < 0) {
            throw new IllegalStateException("Saldo negativo não é permitido");
        }
        quantidadeEstoque = anterior + delta;
        return anterior;
    }

    public void atualizarCusto(BigDecimal novoCusto) {
        this.precoCusto = novoCusto;
    }

    public void desativar() {
        this.ativo = false;
    }

    public void ativar() {
        this.ativo = true;
    }

    /** Abaixo do mínimo = saldo menor ou IGUAL ao mínimo. */
    public boolean isAbaixoDoMinimo() {
        return quantidadeEstoque <= estoqueMinimo;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public CategoriaProduto getCategoria() {
        return categoria;
    }

    public Fornecedor getFornecedorPadrao() {
        return fornecedorPadrao;
    }

    public String getCodigoBarras() {
        return codigoBarras;
    }

    public BigDecimal getPrecoCusto() {
        return precoCusto;
    }

    public BigDecimal getPrecoVenda() {
        return precoVenda;
    }

    public int getQuantidadeEstoque() {
        return quantidadeEstoque;
    }

    public int getEstoqueMinimo() {
        return estoqueMinimo;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
