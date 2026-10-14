import Link from "next/link";
import { BadgeLote } from "@/components/estoque-badges";
import { formatarData } from "@/lib/format";
import { textoPrazo, type Lote } from "@/lib/lotes";

/** Lotes em cartões (celular) e tabela (a partir de md). Cada linha leva à rastreabilidade e ao produto. */
export function LotesLista({ lotes, hoje }: { lotes: Lote[]; hoje: string }) {
  if (lotes.length === 0) {
    return <p className="cartao p-8 text-center text-sm text-muted">Nenhum lote encontrado com estes filtros.</p>;
  }
  return (
    <>
      <ul className="space-y-3 md:hidden" aria-label="Lotes">
        {lotes.map((l) => (
          <li key={l.id} className="cartao space-y-1 p-4 text-sm">
            <div className="flex items-start justify-between gap-2">
              <Link href={`/produtos/${l.produtoId}?aba=lotes`} className="font-medium hover:text-brand">{l.produtoNome}</Link>
              <BadgeLote lote={l} />
            </div>
            <p className="tabular-nums">
              Lote <strong>{l.codigoLote}</strong> · saldo <strong>{l.saldo}</strong>
            </p>
            <p className="text-muted">{l.validade ? `${formatarData(l.validade)} · ${textoPrazo(l.validade, hoje)}` : "Sem validade"}</p>
            <Link href={`/estoque/lotes/${l.id}`} className="font-medium text-brand hover:underline">Rastreabilidade →</Link>
          </li>
        ))}
      </ul>
      <div className="cartao hidden overflow-x-auto md:block">
        <table className="w-full text-left text-sm">
          <caption className="sr-only">Lotes de estoque</caption>
          <thead className="border-b border-border text-xs uppercase tracking-wide text-muted">
            <tr>
              <th scope="col" className="px-4 py-3 font-medium">Produto</th>
              <th scope="col" className="px-4 py-3 font-medium">Lote</th>
              <th scope="col" className="px-4 py-3 font-medium">Validade</th>
              <th scope="col" className="px-4 py-3 text-right font-medium">Saldo</th>
              <th scope="col" className="px-4 py-3 font-medium">Situação</th>
              <th scope="col" className="px-4 py-3 font-medium"><span className="sr-only">Rastreio</span></th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {lotes.map((l) => (
              <tr key={l.id}>
                <td className="px-4 py-3">
                  <Link href={`/produtos/${l.produtoId}?aba=lotes`} className="font-medium hover:text-brand">{l.produtoNome}</Link>
                </td>
                <td className="px-4 py-3 tabular-nums">{l.codigoLote}</td>
                <td className="whitespace-nowrap px-4 py-3 tabular-nums">
                  {l.validade ? formatarData(l.validade) : "—"}
                  {l.validade && <span className="block text-xs text-muted">{textoPrazo(l.validade, hoje)}</span>}
                </td>
                <td className="px-4 py-3 text-right font-semibold tabular-nums">{l.saldo}</td>
                <td className="px-4 py-3"><BadgeLote lote={l} /></td>
                <td className="px-4 py-3 text-right">
                  <Link href={`/estoque/lotes/${l.id}`} className="text-brand hover:underline">Rastreio →</Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
