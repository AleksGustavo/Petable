import type { Metadata } from "next";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { BadgeLote } from "@/components/estoque-badges";
import { moeda } from "@/lib/agendamentos";
import { ApiError, apiFetch } from "@/lib/api";
import { formatarInstante, hojeISO } from "@/lib/datas";
import { rotuloMovimentacao } from "@/lib/estoque";
import { formatarData } from "@/lib/format";
import { textoPrazo, type RastreioLote } from "@/lib/lotes";
import { podeVerLotes } from "@/lib/permissoes";
import { exigirSessao } from "@/lib/session";

export const metadata: Metadata = { title: "Rastreabilidade do lote" };

async function carregar(id: string): Promise<RastreioLote> {
  if (!/^\d+$/.test(id)) notFound();
  try {
    return await apiFetch<RastreioLote>(`/api/v1/lotes/${id}/rastreio`);
  } catch (e) {
    if (e instanceof ApiError && e.problem.status === 404) notFound();
    throw e;
  }
}

/** Rastreabilidade: de onde o lote entrou, para quais vendas saiu e cada movimento. Sem dado de cliente (privacidade). */
export default async function RastreioPage(props: PageProps<"/estoque/lotes/[id]">) {
  const sessao = await exigirSessao();
  if (!podeVerLotes(sessao.papel)) redirect("/dashboard");
  const { id } = await props.params;
  const r = await carregar(id);
  const l = r.lote;
  const hoje = hojeISO();
  const sinal = (n: number) => (n > 0 ? `+${n}` : `−${Math.abs(n)}`);

  return (
    <div className="space-y-6">
      <Link href={`/produtos/${l.produtoId}?aba=lotes`} className="text-sm text-muted hover:text-foreground">← {l.produtoNome}</Link>
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Lote {l.codigoLote}</h1>
          <p className="text-sm text-muted">{l.produtoNome}</p>
        </div>
        <BadgeLote lote={l} />
      </header>

      <dl className="cartao grid gap-4 p-4 sm:grid-cols-4 sm:p-6">
        <div><dt className="text-xs uppercase text-muted">Saldo</dt><dd className="text-2xl font-semibold tabular-nums">{l.saldo}</dd></div>
        <div>
          <dt className="text-xs uppercase text-muted">Validade</dt>
          <dd className="font-medium">{l.validade ? formatarData(l.validade) : "Sem validade"}<span className="block text-xs text-muted">{textoPrazo(l.validade, hoje)}</span></dd>
        </div>
        <div><dt className="text-xs uppercase text-muted">Recebido em</dt><dd className="font-medium">{formatarData(l.dataRecebimento)}</dd></div>
        <div><dt className="text-xs uppercase text-muted">Custo da 1ª entrada</dt><dd className="font-medium tabular-nums">{l.custoUnitario === null ? "—" : moeda(l.custoUnitario)}</dd></div>
      </dl>

      <section className="cartao p-4 sm:p-6" aria-labelledby="sec-entradas">
        <h2 id="sec-entradas" className="mb-3 text-sm font-semibold text-muted">Entradas ({r.entradas.length})</h2>
        {r.entradas.length === 0 ? (
          <p className="text-sm text-muted">Sem compra vinculada (lote criado por regularização ou ajuste).</p>
        ) : (
          <ul className="divide-y divide-border text-sm">
            {r.entradas.map((e) => (
              <li key={e.compraId} className="flex flex-wrap items-baseline justify-between gap-2 py-2">
                <span>
                  <Link href={`/compras/${e.compraId}`} className="font-medium text-brand hover:underline">Compra #{e.compraId}</Link>
                  <span className="text-muted"> · {e.fornecedor.nome} · entrega {formatarData(e.dataEntrega ?? e.dataCompra)}</span>
                </span>
                <span className="tabular-nums">{e.quantidade} un. · {moeda(e.custoUnitario)}</span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="cartao p-4 sm:p-6" aria-labelledby="sec-saidas">
        <h2 id="sec-saidas" className="mb-3 text-sm font-semibold text-muted">Saídas por venda ({r.saidas.length})</h2>
        {r.saidas.length === 0 ? (
          <p className="text-sm text-muted">Nenhuma venda saiu deste lote.</p>
        ) : (
          <ul className="divide-y divide-border text-sm">
            {r.saidas.map((s) => (
              <li key={s.vendaId} className="flex flex-wrap items-baseline justify-between gap-2 py-2">
                <span>
                  <Link href={`/vendas/${s.vendaId}`} className="font-medium text-brand hover:underline">Venda #{s.vendaId}</Link>
                  <span className="text-muted"> · {formatarInstante(s.dataVenda)}{s.statusVenda === "CANCELADA" ? " · cancelada" : ""}</span>
                </span>
                <span className="tabular-nums">{s.quantidade} un.</span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="cartao overflow-x-auto" aria-labelledby="sec-mov">
        <h2 id="sec-mov" className="px-4 pt-4 text-sm font-semibold text-muted sm:px-6">
          Movimentos ({r.totalMovimentos}{r.totalMovimentos > r.movimentos.length ? `, mostrando os ${r.movimentos.length} primeiros` : ""})
        </h2>
        <table className="mt-2 w-full text-left text-sm">
          <thead className="border-b border-border text-xs uppercase tracking-wide text-muted">
            <tr>
              <th scope="col" className="px-4 py-3 font-medium">Data</th>
              <th scope="col" className="px-4 py-3 font-medium">Tipo</th>
              <th scope="col" className="px-4 py-3 text-right font-medium">Qtd.</th>
              <th scope="col" className="px-4 py-3 text-right font-medium">Saldo do lote</th>
              <th scope="col" className="px-4 py-3 font-medium">Origem / motivo</th>
              <th scope="col" className="px-4 py-3 font-medium">Usuário</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {r.movimentos.map((m) => (
              <tr key={m.id}>
                <td className="whitespace-nowrap px-4 py-3 tabular-nums">{formatarInstante(m.createdAt)}</td>
                <td className="px-4 py-3">{rotuloMovimentacao(m.tipo as never)}</td>
                <td className="px-4 py-3 text-right font-semibold tabular-nums">{sinal(m.quantidade)}</td>
                <td className="whitespace-nowrap px-4 py-3 text-right tabular-nums">{m.loteSaldoAnterior} → {m.loteSaldoResultante}</td>
                <td className="px-4 py-3">
                  {m.compraId ? <Link href={`/compras/${m.compraId}`} className="text-brand hover:underline">Compra #{m.compraId}</Link> : m.vendaId ? <Link href={`/vendas/${m.vendaId}`} className="text-brand hover:underline">Venda #{m.vendaId}</Link> : <span className="text-muted">{m.motivo}</span>}
                </td>
                <td className="px-4 py-3">{m.usuario.nome}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </div>
  );
}
