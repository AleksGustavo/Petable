package br.com.tecnodreamer.petconnect.relatorio.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Respostas dos relatórios (somente leitura). */
public final class Relatorios {

    private Relatorios() {
    }

    public record Contagem(long quantidade, BigDecimal valor) {
    }

    // vendas
    public record VendasDia(LocalDate data, long quantidade, BigDecimal liquido) {
    }

    public record VendasGrupo(String chave, long quantidade, BigDecimal liquido) {
    }

    public record Vendas(LocalDate dataInicio, LocalDate dataFim, long quantidade, BigDecimal bruto, BigDecimal descontos,
            BigDecimal liquido, BigDecimal ticketMedio, Contagem canceladas, List<VendasDia> porDia, List<VendasGrupo> porForma,
            List<VendasGrupo> porOperador) {
    }

    // produtos
    public record ProdutoVendido(Long produtoId, String nome, String categoria, long quantidade, BigDecimal faturamento,
            BigDecimal participacao) {
    }

    public record MaisVendidos(LocalDate dataInicio, LocalDate dataFim, List<ProdutoVendido> itens, BigDecimal total) {
    }

    public record ItemAbc(Long produtoId, String nome, BigDecimal faturamento, BigDecimal participacao, BigDecimal acumulado,
            String classe) {
    }

    public record ResumoAbc(String classe, long produtos, BigDecimal faturamento, BigDecimal participacao) {
    }

    public record CurvaAbc(LocalDate dataInicio, LocalDate dataFim, List<ItemAbc> itens, List<ResumoAbc> resumo, BigDecimal total) {
    }

    // estoque e margem (ADMIN)
    public record ItemEstoque(Long produtoId, String nome, long saldoAtual, BigDecimal custoMedio, BigDecimal valorEstoque,
            long quantidadeVendida, BigDecimal giro, BigDecimal coberturaDias) {
    }

    public record Estoque(LocalDate dataInicio, LocalDate dataFim, List<ItemEstoque> itens, BigDecimal valorTotalEstoque,
            long produtosSemCusto, String aviso) {
    }

    public record ItemMargem(Long produtoId, String nome, long quantidade, BigDecimal receita, BigDecimal custoEstimado,
            BigDecimal margem, BigDecimal margemPercentual) {
    }

    public record Margem(LocalDate dataInicio, LocalDate dataFim, BigDecimal receita, BigDecimal custoEstimado, BigDecimal margem,
            BigDecimal margemPercentual, long produtosSemCusto, List<ItemMargem> porProduto, String aviso) {
    }

    // serviços, despesas, caixas e contas (ADMIN)
    public record Servicos(LocalDate dataInicio, LocalDate dataFim, long quantidade, BigDecimal total,
            List<VendasGrupo> porServico, List<VendasGrupo> porFuncionario) {
    }

    public record DespesaCategoria(String categoria, BigDecimal valor) {
    }

    public record Despesas(LocalDate dataInicio, LocalDate dataFim, BigDecimal total, List<DespesaCategoria> porCategoria) {
    }

    public record CaixaOperador(String operador, long caixas, BigDecimal diferenca, long faltas, long sobras) {
    }

    public record CaixaFechado(Long id, String operador, LocalDateTime abertoEm, LocalDateTime fechadoEm, BigDecimal diferencaTotal) {
    }

    public record Caixas(LocalDate dataInicio, LocalDate dataFim, long quantidade, BigDecimal diferencaTotal,
            List<CaixaOperador> porOperador, List<CaixaFechado> caixas) {
    }

    public record ContaPaga(Long id, String descricao, BigDecimal valor, LocalDateTime pagaEm, String formaPagamento) {
    }

    public record Contas(LocalDate dataInicio, LocalDate dataFim, Contagem pagas, Contagem vencidasEmAberto,
            Contagem aVencerEmAberto, List<ContaPaga> itensPagos) {
    }
}
