import Link from "next/link";
import { moeda } from "@/lib/agendamentos";
import { formatarDataHora } from "@/lib/datas";
import { FORMAS_PAGAMENTO, rotuloForma, STATUS_VENDA, type StatusVenda, type Venda } from "@/lib/vendas";

const base = "inline-flex items-center gap-1 whitespace-nowrap rounded-full border px-2.5 py-0.5 text-xs font-medium";

export function BadgeStatusVenda({ status }: { status: StatusVenda }) {
  return status === "CONCLUIDA" ? (
    <span className={`${base} border-brand/40 bg-brand-soft text-brand-strong`}>✓ Concluída</span>
  ) : (
    <span className={`${base} border-danger/40 bg-danger-soft text-danger`}>✕ Cancelada</span>
  );
}

export type FiltrosVendas = { dataInicio: string; dataFim: string; status: string; formaPagamento: string };

export function VendasFiltros({ filtros }: { filtros: FiltrosVendas }) {
  return (
    <form role="search" className="grid gap-2 sm:grid-cols-2 lg:grid-cols-[10rem_10rem_1fr_1fr_auto] lg:items-end">
      <div>
        <label htmlFor="filtro-venda-inicio" className="mb-1 block text-xs text-muted">De</label>
        <input id="filtro-venda-inicio" type="date" name="dataInicio" defaultValue={filtros.dataInicio} className="campo" />
      </div>
      <div>
        <label htmlFor="filtro-venda-fim" className="mb-1 block text-xs text-muted">Até</label>
        <input id="filtro-venda-fim" type="date" name="dataFim" defaultValue={filtros.dataFim} className="campo" />
      </div>
      <div>
        <label htmlFor="filtro-venda-status" className="mb-1 block text-xs text-muted">Status</label>
        <select id="filtro-venda-status" name="status" defaultValue={filtros.status} className="campo">
          <option value="">Todos</option>
          {STATUS_VENDA.map((s) => (
            <option key={s.valor} value={s.valor}>{s.rotulo}</option>
          ))}
        </select>
      </div>
      <div>
        <label htmlFor="filtro-venda-pagamento" className="mb-1 block text-xs text-muted">Pagamento</label>
        <select id="filtro-venda-pagamento" name="formaPagamento" defaultValue={filtros.formaPagamento} className="campo">
          <option value="">Todas</option>
          {FORMAS_PAGAMENTO.map((f) => (
            <option key={f.valor} value={f.valor}>{f.rotulo}</option>
          ))}
        </select>
      </div>
      <button type="submit" className="botao-secundario">Filtrar</button>
    </form>
  );
}

export function VendasLista({ vendas, filtrado = false }: { vendas: Venda[]; filtrado?: boolean }) {
  if (vendas.length === 0) {
    return (
      <p className="cartao p-10 text-center text-sm text-muted">
        {filtrado ? "Nenhuma venda encontrada com os filtros selecionados." : "Nenhuma venda registrada."}
      </p>
    );
  }
  return (
    <>
      <ul className="space-y-3 md:hidden">
        {vendas.map((v) => (
          <li key={v.id}>
            <Link href={`/vendas/${v.id}`} className={`cartao block space-y-1 p-4 transition hover:border-brand ${v.status === "CANCELADA" ? "opacity-70" : ""}`}>
              <div className="flex items-start justify-between gap-2">
                <p className="font-medium">#{v.id} · {formatarDataHora(v.dataVenda)}</p>
                <BadgeStatusVenda status={v.status} />
              </div>
              <p className="text-sm text-muted">
                {rotuloForma(v.formaPagamento)}
                {v.cliente && ` · ${v.cliente.nome}`}
              </p>
              <p className="font-semibold tabular-nums">{moeda(v.valorTotal)}</p>
            </Link>
          </li>
        ))}
      </ul>
      <div className="cartao hidden overflow-x-auto md:block">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-border text-xs uppercase tracking-wide text-muted">
            <tr>
              <th scope="col" className="px-4 py-3 font-medium">Nº</th>
              <th scope="col" className="px-4 py-3 font-medium">Data</th>
              <th scope="col" className="px-4 py-3 font-medium">Cliente</th>
              <th scope="col" className="px-4 py-3 font-medium">Pagamento</th>
              <th scope="col" className="hidden px-4 py-3 font-medium lg:table-cell">Operador</th>
              <th scope="col" className="px-4 py-3 text-right font-medium">Total</th>
              <th scope="col" className="px-4 py-3 font-medium">Status</th>
              <th scope="col" className="px-4 py-3"><span className="sr-only">Ações</span></th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {vendas.map((v) => (
              <tr key={v.id} className={`hover:bg-brand-soft/40 ${v.status === "CANCELADA" ? "text-muted" : ""}`}>
                <td className="px-4 py-3 tabular-nums">#{v.id}</td>
                <td className="whitespace-nowrap px-4 py-3 tabular-nums">{formatarDataHora(v.dataVenda)}</td>
                <td className="px-4 py-3">{v.cliente?.nome ?? "—"}</td>
                <td className="px-4 py-3">{rotuloForma(v.formaPagamento)}</td>
                <td className="hidden px-4 py-3 lg:table-cell">{v.operador?.nome ?? "—"}</td>
                <td className="px-4 py-3 text-right tabular-nums">{moeda(v.valorTotal)}</td>
                <td className="px-4 py-3"><BadgeStatusVenda status={v.status} /></td>
                <td className="px-4 py-3 text-right">
                  <Link href={`/vendas/${v.id}`} className="font-medium text-brand hover:underline">Abrir</Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
