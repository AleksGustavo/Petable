package br.com.tecnodreamer.petconnect.relatorio.application;

import br.com.tecnodreamer.petconnect.relatorio.web.Relatorios;
import br.com.tecnodreamer.petconnect.shared.error.NotFoundException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Monta o CSV de cada relatório com cabeçalhos em português (DA-10). */
@Component
public class RelatorioCsv {

    /** Relatórios liberados também para VENDAS (DA-09); os demais são só ADMIN. */
    public static final Set<String> DE_VENDAS = Set.of("vendas", "produtos-mais-vendidos", "curva-abc");
    public static final Set<String> TIPOS = Set.of("vendas", "produtos-mais-vendidos", "curva-abc", "estoque", "margem", "servicos",
            "despesas", "caixas", "contas");

    public record Arquivo(byte[] conteudo, int linhas) {
    }

    private final RelatorioService relatorios;

    public RelatorioCsv(RelatorioService relatorios) {
        this.relatorios = relatorios;
    }

    public Arquivo gerar(String tipo, Periodo p, int limite) {
        List<String> cab;
        List<List<Object>> linhas = new ArrayList<>();
        switch (tipo) {
            case "vendas" -> {
                Relatorios.Vendas r = relatorios.vendas(p);
                cab = List.of("Data", "Vendas", "Faturamento líquido (R$)");
                r.porDia().forEach(d -> linhas.add(List.of(d.data(), d.quantidade(), d.liquido())));
                linhas.add(List.of("Total", r.quantidade(), r.liquido()));
            }
            case "produtos-mais-vendidos" -> {
                cab = List.of("Produto", "Categoria", "Quantidade", "Faturamento (R$)", "Participação (%)");
                relatorios.maisVendidos(p, limite).itens().forEach(i -> linhas.add(List.of(i.nome(), i.categoria(), i.quantidade(),
                        i.faturamento(), i.participacao())));
            }
            case "curva-abc" -> {
                cab = List.of("Produto", "Faturamento (R$)", "Participação (%)", "Acumulado (%)", "Classe");
                relatorios.curvaAbc(p).itens().forEach(i -> linhas.add(List.of(i.nome(), i.faturamento(), i.participacao(), i.acumulado(),
                        i.classe())));
            }
            case "estoque" -> {
                cab = List.of("Produto", "Saldo atual", "Custo médio (R$)", "Valor em estoque (R$)", "Vendido no período", "Giro",
                        "Cobertura (dias)");
                relatorios.estoque(p).itens().forEach(i -> linhas.add(lista(i.nome(), i.saldoAtual(), i.custoMedio(), i.valorEstoque(),
                        i.quantidadeVendida(), i.giro(), i.coberturaDias())));
            }
            case "margem" -> {
                cab = List.of("Produto", "Quantidade", "Receita (R$)", "Custo estimado (R$)", "Margem (R$)", "Margem (%)");
                relatorios.margem(p).porProduto().forEach(i -> linhas.add(List.of(i.nome(), i.quantidade(), i.receita(), i.custoEstimado(),
                        i.margem(), i.margemPercentual())));
            }
            case "servicos" -> {
                cab = List.of("Serviço", "Quantidade", "Valor (R$)");
                relatorios.servicos(p).porServico().forEach(g -> linhas.add(List.of(g.chave(), g.quantidade(), g.liquido())));
            }
            case "despesas" -> {
                cab = List.of("Categoria", "Valor (R$)");
                relatorios.despesas(p).porCategoria().forEach(d -> linhas.add(List.of(ROTULO_CATEGORIA.getOrDefault(d.categoria(),
                        d.categoria()), d.valor())));
            }
            case "caixas" -> {
                cab = List.of("Caixa", "Operador", "Abertura", "Fechamento", "Diferença (R$)");
                relatorios.caixas(p).caixas().forEach(c -> linhas.add(List.of(c.id(), c.operador(), c.abertoEm(), c.fechadoEm(),
                        c.diferencaTotal())));
            }
            case "contas" -> {
                cab = List.of("Conta", "Descrição", "Valor (R$)", "Paga em", "Forma");
                relatorios.contas(p).itensPagos().forEach(c -> linhas.add(lista(c.id(), c.descricao(), c.valor(), c.pagaEm(),
                        c.formaPagamento())));
            }
            default -> throw new NotFoundException("Relatório", tipo);
        }
        return new Arquivo(Csv.gerar(cab, linhas), linhas.size());
    }

    /** List.of não aceita nulos (giro/cobertura podem ser nulos). */
    private static List<Object> lista(Object... valores) {
        return new ArrayList<>(java.util.Arrays.asList(valores));
    }

    private static final Map<String, String> ROTULO_CATEGORIA = Map.of("COMPRA_MERCADORIA", "Compra de mercadoria",
            "DESPESA_FUNCIONARIO", "Despesa com funcionário", "DESPESA_OPERACIONAL", "Despesa operacional", "OUTROS", "Outros");
}
