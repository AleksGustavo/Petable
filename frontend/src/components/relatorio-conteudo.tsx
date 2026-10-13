import { Aviso } from "@/components/aviso";
import { Diferenca } from "@/components/financeiro-badges";
import { BarrasHorizontais, Indicadores, TabelaResponsiva } from "@/components/relatorio-ui";
import { moeda } from "@/lib/agendamentos";
import { formatarDataHora } from "@/lib/datas";
import { rotuloCategoria } from "@/lib/financeiro";
import { formatarData } from "@/lib/format";
import type {
  ClasseAbc,
  DadosRelatorio,
  RelCaixas,
  RelContas,
  RelCurvaAbc,
  RelDespesas,
  RelEstoque,
  RelMaisVendidos,
  RelMargem,
  RelServicos,
  RelVendas,
} from "@/lib/relatorios";
import { rotuloForma, type FormaPagamento } from "@/lib/vendas";

const num = (v: number) => new Intl.NumberFormat("pt-BR").format(v);
const pct = (v: number) => `${new Intl.NumberFormat("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(v)}%`;
const opcional = (v: number | null, sufixo = "") => (v === null ? "—" : `${new Intl.NumberFormat("pt-BR").format(v)}${sufixo}`);

export function RelatorioVendas({ d }: { d: RelVendas }) {
  return (
    <div className="space-y-6">
      <Indicadores
        itens={[
          { rotulo: "Faturamento líquido", valor: moeda(d.liquido), destaque: "positivo" },
          { rotulo: "Vendas concluídas", valor: num(d.quantidade) },
          { rotulo: "Ticket médio", valor: moeda(d.ticketMedio) },
          { rotulo: "Descontos", valor: moeda(d.descontos) },
        ]}
      />
      <p className="text-sm text-muted">
        Bruto {moeda(d.bruto)} · Canceladas (fora do faturamento): {num(d.canceladas.quantidade)} ({moeda(d.canceladas.valor)})
      </p>
      <BarrasHorizontais titulo="Faturamento por dia" itens={d.porDia.map((p) => ({ rotulo: formatarData(p.data), valor: p.liquido }))} formatar={moeda} />
      <TabelaResponsiva
        titulo="Por dia"
        colunas={[{ titulo: "Dia" }, { titulo: "Vendas", direita: true }, { titulo: "Líquido", direita: true }]}
        linhas={d.porDia.map((p) => ({ chave: p.data, celulas: [formatarData(p.data), num(p.quantidade), moeda(p.liquido)] }))}
      />
      <div className="grid gap-6 lg:grid-cols-2">
        <TabelaResponsiva
          titulo="Por forma de pagamento"
          colunas={[{ titulo: "Forma" }, { titulo: "Vendas", direita: true }, { titulo: "Líquido", direita: true }]}
          linhas={d.porForma.map((g) => ({ chave: g.chave, celulas: [rotuloForma(g.chave as FormaPagamento), num(g.quantidade), moeda(g.liquido)] }))}
        />
        <TabelaResponsiva
          titulo="Por operador"
          colunas={[{ titulo: "Operador" }, { titulo: "Vendas", direita: true }, { titulo: "Líquido", direita: true }]}
          linhas={d.porOperador.map((g) => ({ chave: g.chave, celulas: [g.chave, num(g.quantidade), moeda(g.liquido)] }))}
        />
      </div>
    </div>
  );
}

export function RelatorioMaisVendidos({ d }: { d: RelMaisVendidos }) {
  return (
    <div className="space-y-6">
      <Indicadores itens={[{ rotulo: "Faturamento de produtos", valor: moeda(d.total), destaque: "positivo" }, { rotulo: "Produtos listados", valor: num(d.itens.length) }]} />
      <BarrasHorizontais titulo="Quantidade vendida" itens={d.itens.map((i) => ({ rotulo: i.nome, valor: i.quantidade }))} formatar={num} />
      <TabelaResponsiva
        titulo="Ranking"
        colunas={[{ titulo: "Produto" }, { titulo: "Categoria" }, { titulo: "Quantidade", direita: true }, { titulo: "Faturamento", direita: true }, { titulo: "Participação", direita: true }]}
        linhas={d.itens.map((i) => ({ chave: i.produtoId, celulas: [i.nome, i.categoria, num(i.quantidade), moeda(i.faturamento), pct(i.participacao)] }))}
      />
    </div>
  );
}

const ESTILO_CLASSE: Record<ClasseAbc, string> = {
  A: "border-brand/40 bg-brand-soft text-brand-strong",
  B: "border-amber-500/40 bg-amber-500/10 text-amber-700 dark:text-amber-300",
  C: "border-border text-muted",
};

export function BadgeClasse({ classe }: { classe: ClasseAbc }) {
  return <span className={`inline-flex rounded-full border px-2.5 py-0.5 text-xs font-semibold ${ESTILO_CLASSE[classe]}`}>Classe {classe}</span>;
}

export function RelatorioCurvaAbc({ d }: { d: RelCurvaAbc }) {
  return (
    <div className="space-y-6">
      <dl className="grid gap-3 sm:grid-cols-3">
        {d.resumo.map((r) => (
          <div key={r.classe} className="cartao p-4">
            <dt><BadgeClasse classe={r.classe} /></dt>
            <dd className="mt-2 text-sm">
              <strong className="text-lg tabular-nums">{num(r.produtos)}</strong> produto(s) · {moeda(r.faturamento)} ({pct(r.participacao)})
            </dd>
          </div>
        ))}
      </dl>
      <p className="text-sm text-muted">Classe pelo acumulado antes do produto: até 80% = A, até 95% = B, restante = C.</p>
      <TabelaResponsiva
        titulo="Produtos"
        colunas={[{ titulo: "Produto" }, { titulo: "Faturamento", direita: true }, { titulo: "Participação", direita: true }, { titulo: "Acumulado", direita: true }, { titulo: "Classe" }]}
        linhas={d.itens.map((i) => ({
          chave: i.produtoId,
          celulas: [i.nome, moeda(i.faturamento), pct(i.participacao), pct(i.acumulado), <BadgeClasse key="c" classe={i.classe} />],
        }))}
      />
    </div>
  );
}

export function RelatorioEstoque({ d }: { d: RelEstoque }) {
  return (
    <div className="space-y-6">
      <Aviso>{d.aviso}{d.produtosSemCusto > 0 && ` ${d.produtosSemCusto} produto(s) sem custo entram como R$ 0,00.`}</Aviso>
      <Indicadores itens={[{ rotulo: "Valor total em estoque", valor: moeda(d.valorTotalEstoque) }, { rotulo: "Produtos ativos", valor: num(d.itens.length) }]} />
      <TabelaResponsiva
        titulo="Produtos"
        colunas={[
          { titulo: "Produto" },
          { titulo: "Saldo", direita: true },
          { titulo: "Custo médio", direita: true },
          { titulo: "Valor", direita: true },
          { titulo: "Vendido", direita: true },
          { titulo: "Giro", direita: true },
          { titulo: "Cobertura", direita: true },
        ]}
        linhas={d.itens.map((i) => ({
          chave: i.produtoId,
          celulas: [i.nome, num(i.saldoAtual), moeda(i.custoMedio), moeda(i.valorEstoque), num(i.quantidadeVendida), opcional(i.giro), opcional(i.coberturaDias, " dias")],
        }))}
      />
    </div>
  );
}

export function RelatorioMargem({ d }: { d: RelMargem }) {
  return (
    <div className="space-y-6">
      <Aviso tipo="erro">
        {d.aviso}
        {d.produtosSemCusto > 0 && ` ${d.produtosSemCusto} produto(s) sem custo cadastrado entram com custo zero.`}
      </Aviso>
      <Indicadores
        itens={[
          { rotulo: "Receita de produtos", valor: moeda(d.receita) },
          { rotulo: "Custo estimado", valor: moeda(d.custoEstimado) },
          { rotulo: "Margem estimada", valor: moeda(d.margem), destaque: d.margem < 0 ? "negativo" : "positivo" },
          { rotulo: "Margem %", valor: pct(d.margemPercentual) },
        ]}
      />
      <TabelaResponsiva
        titulo="Por produto"
        colunas={[{ titulo: "Produto" }, { titulo: "Qtd.", direita: true }, { titulo: "Receita", direita: true }, { titulo: "Custo est.", direita: true }, { titulo: "Margem", direita: true }, { titulo: "Margem %", direita: true }]}
        linhas={d.porProduto.map((i) => ({
          chave: i.produtoId,
          celulas: [i.nome, num(i.quantidade), moeda(i.receita), moeda(i.custoEstimado), moeda(i.margem), pct(i.margemPercentual)],
        }))}
      />
    </div>
  );
}

export function RelatorioServicos({ d }: { d: RelServicos }) {
  const colunas = (primeira: string) => [{ titulo: primeira }, { titulo: "Quantidade", direita: true }, { titulo: "Valor", direita: true }];
  return (
    <div className="space-y-6">
      <Indicadores itens={[{ rotulo: "Receita de serviços", valor: moeda(d.total), destaque: "positivo" }, { rotulo: "Serviços realizados", valor: num(d.quantidade) }]} />
      <BarrasHorizontais titulo="Receita por serviço" itens={d.porServico.map((g) => ({ rotulo: g.chave, valor: g.liquido }))} formatar={moeda} />
      <div className="grid gap-6 lg:grid-cols-2">
        <TabelaResponsiva titulo="Por serviço" colunas={colunas("Serviço")} linhas={d.porServico.map((g) => ({ chave: g.chave, celulas: [g.chave, num(g.quantidade), moeda(g.liquido)] }))} />
        <TabelaResponsiva titulo="Por funcionário" colunas={colunas("Funcionário")} linhas={d.porFuncionario.map((g) => ({ chave: g.chave, celulas: [g.chave, num(g.quantidade), moeda(g.liquido)] }))} />
      </div>
    </div>
  );
}

export function RelatorioDespesas({ d }: { d: RelDespesas }) {
  return (
    <div className="space-y-6">
      <Indicadores itens={[{ rotulo: "Despesas no período", valor: moeda(d.total), destaque: "negativo" }]} />
      <p className="text-sm text-muted">Sangrias e suprimentos são movimento interno de caixa e não entram; estornos são descontados.</p>
      <BarrasHorizontais titulo="Por categoria" itens={d.porCategoria.map((c) => ({ rotulo: rotuloCategoria(c.categoria), valor: c.valor }))} formatar={moeda} />
      <TabelaResponsiva
        titulo="Categorias"
        colunas={[{ titulo: "Categoria" }, { titulo: "Valor", direita: true }]}
        linhas={d.porCategoria.map((c) => ({ chave: c.categoria, celulas: [rotuloCategoria(c.categoria), moeda(c.valor)] }))}
      />
    </div>
  );
}

export function RelatorioCaixas({ d }: { d: RelCaixas }) {
  return (
    <div className="space-y-6">
      <Indicadores
        itens={[
          { rotulo: "Caixas fechados", valor: num(d.quantidade) },
          { rotulo: "Diferença total", valor: moeda(d.diferencaTotal), destaque: d.diferencaTotal < 0 ? "negativo" : undefined },
        ]}
      />
      <TabelaResponsiva
        titulo="Por operador"
        colunas={[{ titulo: "Operador" }, { titulo: "Caixas", direita: true }, { titulo: "Faltas", direita: true }, { titulo: "Sobras", direita: true }, { titulo: "Diferença", direita: true }]}
        linhas={d.porOperador.map((o) => ({
          chave: o.operador,
          celulas: [o.operador, num(o.caixas), num(o.faltas), num(o.sobras), <Diferenca key="d" valor={o.diferenca} formatar={moeda} />],
        }))}
      />
      <TabelaResponsiva
        titulo="Caixas"
        colunas={[{ titulo: "Caixa" }, { titulo: "Operador" }, { titulo: "Fechamento" }, { titulo: "Diferença", direita: true }]}
        linhas={d.caixas.map((c) => ({
          chave: c.id,
          celulas: [`#${c.id}`, c.operador, formatarDataHora(c.fechadoEm), <Diferenca key="d" valor={c.diferencaTotal} formatar={moeda} />],
        }))}
      />
    </div>
  );
}

export function RelatorioContas({ d }: { d: RelContas }) {
  return (
    <div className="space-y-6">
      <Indicadores
        itens={[
          { rotulo: "Pagas no período", valor: `${num(d.pagas.quantidade)} · ${moeda(d.pagas.valor)}` },
          { rotulo: "Vencidas em aberto (hoje)", valor: `${num(d.vencidasEmAberto.quantidade)} · ${moeda(d.vencidasEmAberto.valor)}`, destaque: d.vencidasEmAberto.quantidade > 0 ? "negativo" : undefined },
          { rotulo: "A vencer em aberto (hoje)", valor: `${num(d.aVencerEmAberto.quantidade)} · ${moeda(d.aVencerEmAberto.valor)}` },
        ]}
      />
      <TabelaResponsiva
        titulo="Contas pagas"
        colunas={[{ titulo: "Conta" }, { titulo: "Paga em" }, { titulo: "Forma" }, { titulo: "Valor", direita: true }]}
        linhas={d.itensPagos.map((c) => ({
          chave: c.id,
          celulas: [`${c.descricao} (#${c.id})`, formatarDataHora(c.pagaEm), rotuloForma(c.formaPagamento as FormaPagamento), moeda(c.valor)],
        }))}
      />
    </div>
  );
}

export function ConteudoRelatorio({ relatorio }: { relatorio: DadosRelatorio }) {
  switch (relatorio.tipo) {
    case "vendas":
      return <RelatorioVendas d={relatorio.dados} />;
    case "produtos-mais-vendidos":
      return <RelatorioMaisVendidos d={relatorio.dados} />;
    case "curva-abc":
      return <RelatorioCurvaAbc d={relatorio.dados} />;
    case "estoque":
      return <RelatorioEstoque d={relatorio.dados} />;
    case "margem":
      return <RelatorioMargem d={relatorio.dados} />;
    case "servicos":
      return <RelatorioServicos d={relatorio.dados} />;
    case "despesas":
      return <RelatorioDespesas d={relatorio.dados} />;
    case "caixas":
      return <RelatorioCaixas d={relatorio.dados} />;
    case "contas":
      return <RelatorioContas d={relatorio.dados} />;
  }
}
