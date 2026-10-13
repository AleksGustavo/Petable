import Link from "next/link";
import { BadgeStatusCompra } from "@/components/estoque-badges";
import { moeda } from "@/lib/agendamentos";
import type { Compra } from "@/lib/estoque";
import { formatarData } from "@/lib/format";

export function ComprasLista({ compras, filtrado = false }: { compras: Compra[]; filtrado?: boolean }) {
  if (compras.length === 0) {
    return (
      <p className="cartao p-10 text-center text-sm text-muted">
        {filtrado ? "Nenhuma compra encontrada com os filtros selecionados." : "Nenhuma compra registrada."}
      </p>
    );
  }
  return (
    <>
      <ul className="space-y-3 md:hidden">
        {compras.map((c) => (
          <li key={c.id}>
            <Link href={`/compras/${c.id}`} className="cartao block space-y-1 p-4 transition hover:border-brand">
              <div className="flex items-start justify-between gap-2">
                <p className="font-medium">
                  #{c.id} · {c.fornecedor.nome}
                </p>
                <BadgeStatusCompra status={c.status} />
              </div>
              <p className="text-sm text-muted">
                {formatarData(c.dataCompra)} · {c.formaPagamento}
              </p>
              <p className="font-semibold tabular-nums">{moeda(c.valorTotal)}</p>
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
              <th scope="col" className="px-4 py-3 font-medium">Fornecedor</th>
              <th scope="col" className="px-4 py-3 font-medium">Pagamento</th>
              <th scope="col" className="px-4 py-3 text-right font-medium">Total</th>
              <th scope="col" className="px-4 py-3 font-medium">Status</th>
              <th scope="col" className="px-4 py-3"><span className="sr-only">Ações</span></th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {compras.map((c) => (
              <tr key={c.id} className="hover:bg-brand-soft/40">
                <td className="px-4 py-3 tabular-nums">#{c.id}</td>
                <td className="whitespace-nowrap px-4 py-3 tabular-nums">{formatarData(c.dataCompra)}</td>
                <td className="px-4 py-3 font-medium">{c.fornecedor.nome}</td>
                <td className="px-4 py-3">{c.formaPagamento}</td>
                <td className="px-4 py-3 text-right tabular-nums">{moeda(c.valorTotal)}</td>
                <td className="px-4 py-3"><BadgeStatusCompra status={c.status} /></td>
                <td className="px-4 py-3 text-right">
                  <Link href={`/compras/${c.id}`} className="font-medium text-brand hover:underline">Abrir</Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
