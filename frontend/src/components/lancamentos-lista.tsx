import Link from "next/link";
import { BadgeTipo } from "@/components/financeiro-badges";
import { moeda } from "@/lib/agendamentos";
import { CATEGORIAS, ORIGENS, rotuloCategoria, rotuloOrigem, type Fluxo, type Lancamento } from "@/lib/financeiro";
import { formatarData } from "@/lib/format";
import { FORMAS_PAGAMENTO, rotuloForma, type FormaPagamento } from "@/lib/vendas";

export type FiltrosLancamentos = { dataInicio: string; dataFim: string; tipo: string; categoria: string; formaPagamento: string; origem: string };

export function LancamentosFiltros({ filtros }: { filtros: FiltrosLancamentos }) {
  const select = (id: string, nome: keyof FiltrosLancamentos, rotulo: string, opcoes: { valor: string; rotulo: string }[]) => (
    <div>
      <label htmlFor={id} className="mb-1 block text-xs text-muted">{rotulo}</label>
      <select id={id} name={nome} defaultValue={filtros[nome]} className="campo">
        <option value="">Todos</option>
        {opcoes.map((o) => (
          <option key={o.valor} value={o.valor}>{o.rotulo}</option>
        ))}
      </select>
    </div>
  );
  return (
    <form role="search" className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-[9.5rem_9.5rem_1fr_1fr_1fr_1fr_auto] xl:items-end">
      <div>
        <label htmlFor="fin-inicio" className="mb-1 block text-xs text-muted">De</label>
        <input id="fin-inicio" type="date" name="dataInicio" defaultValue={filtros.dataInicio} className="campo" />
      </div>
      <div>
        <label htmlFor="fin-fim" className="mb-1 block text-xs text-muted">Até</label>
        <input id="fin-fim" type="date" name="dataFim" defaultValue={filtros.dataFim} className="campo" />
      </div>
      {select("fin-tipo", "tipo", "Tipo", [
        { valor: "ENTRADA", rotulo: "Entradas" },
        { valor: "SAIDA", rotulo: "Saídas" },
      ])}
      {select("fin-categoria", "categoria", "Categoria", CATEGORIAS)}
      {select("fin-forma", "formaPagamento", "Forma", FORMAS_PAGAMENTO)}
      {select("fin-origem", "origem", "Origem", ORIGENS)}
      <button type="submit" className="botao-secundario">Filtrar</button>
    </form>
  );
}

function Valor({ l }: { l: Lancamento }) {
  return (
    <span className={`font-semibold tabular-nums ${l.tipo === "ENTRADA" ? "text-brand-strong" : "text-danger"}`}>
      {l.tipo === "ENTRADA" ? "+" : "−"} {moeda(l.valor)}
    </span>
  );
}

export function LancamentosLista({ lancamentos, filtrado = false }: { lancamentos: Lancamento[]; filtrado?: boolean }) {
  if (lancamentos.length === 0) {
    return (
      <p className="cartao p-10 text-center text-sm text-muted">
        {filtrado ? "Nenhum lançamento encontrado com os filtros selecionados." : "Nenhum lançamento registrado."}
      </p>
    );
  }
  return (
    <>
      <ul className="space-y-3 md:hidden">
        {lancamentos.map((l) => (
          <li key={l.id}>
            <Link href={`/financeiro/lancamentos/${l.id}`} className="cartao block space-y-1 p-4 transition hover:border-brand">
              <div className="flex items-start justify-between gap-2">
                <p className="min-w-0 font-medium">{l.descricao}</p>
                <Valor l={l} />
              </div>
              <p className="text-sm text-muted">
                {formatarData(l.dataMovimentacao)} · {rotuloCategoria(l.categoria)}
                {l.formaPagamento && ` · ${rotuloForma(l.formaPagamento)}`}
              </p>
              <BadgeTipo tipo={l.tipo} />
            </Link>
          </li>
        ))}
      </ul>
      <div className="cartao hidden overflow-x-auto md:block">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-border text-xs uppercase tracking-wide text-muted">
            <tr>
              <th scope="col" className="px-4 py-3 font-medium">Data</th>
              <th scope="col" className="px-4 py-3 font-medium">Descrição</th>
              <th scope="col" className="hidden px-4 py-3 font-medium lg:table-cell">Categoria</th>
              <th scope="col" className="px-4 py-3 font-medium">Forma</th>
              <th scope="col" className="hidden px-4 py-3 font-medium lg:table-cell">Origem</th>
              <th scope="col" className="px-4 py-3 text-right font-medium">Valor</th>
              <th scope="col" className="px-4 py-3"><span className="sr-only">Ações</span></th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {lancamentos.map((l) => (
              <tr key={l.id} className="hover:bg-brand-soft/40">
                <td className="whitespace-nowrap px-4 py-3 tabular-nums">{formatarData(l.dataMovimentacao)}</td>
                <td className="px-4 py-3 font-medium">{l.descricao}</td>
                <td className="hidden px-4 py-3 lg:table-cell">{rotuloCategoria(l.categoria)}</td>
                <td className="px-4 py-3">{l.formaPagamento ? rotuloForma(l.formaPagamento) : "—"}</td>
                <td className="hidden px-4 py-3 lg:table-cell">{rotuloOrigem(l.origem)}</td>
                <td className="whitespace-nowrap px-4 py-3 text-right"><Valor l={l} /></td>
                <td className="px-4 py-3 text-right">
                  <Link href={`/financeiro/lancamentos/${l.id}`} className="font-medium text-brand hover:underline">Abrir</Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

const rotuloFormaFluxo = (f: string) => (f === "NAO_INFORMADA" ? "Não informada" : rotuloForma(f as FormaPagamento));

/** Totais do período (agregados pela API) por categoria e por forma. */
export function FluxoResumo({ fluxo }: { fluxo: Fluxo }) {
  const tabela = (titulo: string, linhas: { chave: string; rotulo: string; entradas: number; saidas: number; saldo: number }[]) => (
    <section className="cartao overflow-hidden" aria-label={titulo}>
      <h3 className="px-4 pt-4 text-sm font-semibold text-muted">{titulo}</h3>
      {linhas.length === 0 ? (
        <p className="p-4 text-sm text-muted">Sem movimento no período.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="text-xs uppercase tracking-wide text-muted">
              <tr>
                <th scope="col" className="px-4 py-2 font-medium">&nbsp;</th>
                <th scope="col" className="px-4 py-2 text-right font-medium">Entradas</th>
                <th scope="col" className="px-4 py-2 text-right font-medium">Saídas</th>
                <th scope="col" className="px-4 py-2 text-right font-medium">Saldo</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {linhas.map((l) => (
                <tr key={l.chave}>
                  <td className="px-4 py-2">{l.rotulo}</td>
                  <td className="px-4 py-2 text-right tabular-nums">{moeda(l.entradas)}</td>
                  <td className="px-4 py-2 text-right tabular-nums">{moeda(l.saidas)}</td>
                  <td className={`px-4 py-2 text-right font-medium tabular-nums ${l.saldo < 0 ? "text-danger" : ""}`}>{moeda(l.saldo)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
  return (
    <div className="space-y-4">
      <dl className="grid gap-3 sm:grid-cols-3">
        {[
          ["Entradas", fluxo.entradas, "text-brand-strong"],
          ["Saídas", fluxo.saidas, "text-danger"],
          ["Saldo do período", fluxo.saldo, fluxo.saldo < 0 ? "text-danger" : ""],
        ].map(([rotulo, valor, cor]) => (
          <div key={rotulo as string} className="cartao p-4">
            <dt className="text-sm text-muted">{rotulo}</dt>
            <dd className={`mt-1 text-2xl font-semibold tabular-nums ${cor}`} data-testid={`fluxo-${rotulo}`}>{moeda(valor as number)}</dd>
          </div>
        ))}
      </dl>
      <div className="grid gap-4 lg:grid-cols-2">
        {tabela("Por categoria", fluxo.porCategoria.map((c) => ({ chave: c.categoria, rotulo: rotuloCategoria(c.categoria), ...c })))}
        {tabela("Por forma de pagamento", fluxo.porFormaPagamento.map((f) => ({ chave: f.formaPagamento, rotulo: rotuloFormaFluxo(f.formaPagamento), ...f })))}
      </div>
    </div>
  );
}
