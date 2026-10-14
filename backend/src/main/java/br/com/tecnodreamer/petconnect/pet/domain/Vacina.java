package br.com.tecnodreamer.petconnect.pet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Catálogo de vacinas por espécie (somente leitura nesta etapa). */
@Entity
@Table(name = "vacina")
public class Vacina {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "especie_id")
    private Especie especie;

    private String nome;

    @Column(name = "doenca_protecao")
    private String doencaProtecao;

    @Column(name = "validade_padrao_meses")
    private int validadePadraoMeses;

    private boolean ativo;

    /** Produto de estoque desta vacina; nulo = a aplicação não baixa estoque. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id")
    private br.com.tecnodreamer.petconnect.produto.domain.Produto produto;

    protected Vacina() {
    }

    public boolean indicadaPara(Especie outra) {
        return especie.getId().equals(outra.getId());
    }

    public Long getId() {
        return id;
    }

    public Especie getEspecie() {
        return especie;
    }

    public String getNome() {
        return nome;
    }

    public String getDoencaProtecao() {
        return doencaProtecao;
    }

    public int getValidadePadraoMeses() {
        return validadePadraoMeses;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public br.com.tecnodreamer.petconnect.produto.domain.Produto getProduto() {
        return produto;
    }

    /** Liga (ou, com nulo, desliga) o produto de estoque desta vacina. */
    public void vincularProduto(br.com.tecnodreamer.petconnect.produto.domain.Produto produto) {
        this.produto = produto;
    }
}
