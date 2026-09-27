package br.com.tecnodreamer.petconnect.servico.domain;

import br.com.tecnodreamer.petconnect.pet.domain.Porte;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Entity
@Table(name = "servico")
public class Servico {

    /**
     * Compatibilidade transitória (etapa 02, R02): o schema não tem tipo de
     * serviço, então vacinação é reconhecida só pelo nome exato — nunca por
     * substring.
     */
    public static final String NOME_VACINACAO = "Vacinação";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;
    private String descricao;
    private BigDecimal preco;

    @Column(name = "duracao_minutos")
    private Integer duracaoMinutos;

    private boolean ativo = true;

    @OneToMany(mappedBy = "servico", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PrecoServicoPorte> precos = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Servico() {
    }

    public Servico(String nome, String descricao, BigDecimal preco, int duracaoMinutos, Map<Porte, BigDecimal> precosPorPorte) {
        atualizar(nome, descricao, preco, duracaoMinutos, precosPorPorte);
    }

    /** Estado completo editável. Preços por porte são sincronizados: atualiza, insere e remove os omitidos. */
    public final void atualizar(String nome, String descricao, BigDecimal preco, int duracaoMinutos,
            Map<Porte, BigDecimal> precosPorPorte) {
        this.nome = nome;
        this.descricao = descricao;
        this.preco = preco;
        this.duracaoMinutos = duracaoMinutos;
        precos.removeIf(p -> !precosPorPorte.containsKey(p.getPorte()));
        precosPorPorte.forEach((porte, valor) -> precos.stream()
                .filter(p -> p.getPorte() == porte).findFirst()
                .ifPresentOrElse(p -> p.alterarPreco(valor), () -> precos.add(new PrecoServicoPorte(this, porte, valor))));
    }

    public void desativar() {
        this.ativo = false;
    }

    public boolean isVacinacao() {
        return nome != null && nome.trim().equalsIgnoreCase(NOME_VACINACAO);
    }

    public Optional<BigDecimal> precoPara(Porte porte) {
        return porte == null ? Optional.empty()
                : precos.stream().filter(p -> p.getPorte() == porte).map(PrecoServicoPorte::getPreco).findFirst();
    }

    /** Preço do porte tem precedência; pet sem porte (ou porte sem preço) usa o padrão. Nunca assume PEQUENO. */
    public PrecoEstimado estimarPara(Porte porte) {
        return precoPara(porte)
                .map(v -> new PrecoEstimado(OrigemPreco.PORTE, v))
                .orElseGet(() -> new PrecoEstimado(OrigemPreco.PADRAO, preco));
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

    public BigDecimal getPreco() {
        return preco;
    }

    public Integer getDuracaoMinutos() {
        return duracaoMinutos;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public List<PrecoServicoPorte> getPrecos() {
        return precos.stream().sorted(Comparator.comparing(PrecoServicoPorte::getPorte)).toList();
    }
}
