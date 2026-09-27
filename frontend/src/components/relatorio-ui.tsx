import type { ReactNode } from "react";

/** Cards de indicadores do topo do relatório. */
export function Indicadores({ itens }: { itens: { rotulo: string; valor: string; destaque?: "positivo" | "negativo" }[] }) {
  return (
    <dl className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
      {itens.map((i) => (
        <div key={i.rotulo} className="cartao p-4">
          <dt className="text-sm text-muted">{i.rotulo}</dt>
          <dd
            className={`mt-1 text-2xl font-semibold tabular-nums ${
              i.destaque === "negativo" ? "text-danger" : i.destaque === "positivo" ? "text-brand-strong" : ""
            }`}
          >
            {i.valor}
          </dd>
        </div>
      ))}
    </dl>
  );
}

/**
 * Barras horizontais em CSS (DA-11). As barras são decorativas (aria-hidden): o
 * rótulo e o valor ficam em texto, e a tabela do relatório traz os mesmos números.
 */
export function BarrasHorizontais({
  titulo,
  itens,
  formatar,
}: {
  titulo: string;
  itens: { rotulo: string; valor: number }[];
  formatar: (v: number) => string;
}) {
  const maximo = Math.max(0, ...itens.map((i) => Math.abs(i.valor)));
  if (itens.length === 0) return null;
  return (
    <figure className="cartao space-y-2 p-4" aria-label={titulo}>
      <figcaption className="text-sm font-semibold text-muted">{titulo}</figcaption>
      <ul className="space-y-1.5">
        {itens.map((i) => {
          const largura = maximo === 0 ? 0 : Math.round((Math.abs(i.valor) / maximo) * 100);
          return (
            <li key={i.rotulo} className="grid grid-cols-[minmax(0,8rem)_1fr_auto] items-center gap-2 text-sm sm:grid-cols-[minmax(0,12rem)_1fr_auto]">
              <span className="truncate" title={i.rotulo}>{i.rotulo}</span>
              <span className="h-3 overflow-hidden rounded-full bg-border/40" aria-hidden="true">
                <span
                  className={`block h-full rounded-full ${i.valor < 0 ? "bg-danger" : "bg-brand"}`}
                  style={{ width: `${largura}%` }}
                  data-testid="barra"
                  data-largura={largura}
                />
              </span>
              <span className="whitespace-nowrap text-right tabular-nums">{formatar(i.valor)}</span>
            </li>
          );
        })}
      </ul>
    </figure>
  );
}

export type Coluna = { titulo: string; direita?: boolean };

/** Tabela no md+ e cartões (rótulo: valor) no celular; nunca provoca rolagem horizontal na página. */
export function TabelaResponsiva({
  titulo,
  colunas,
  linhas,
  vazio = "Sem dados no período.",
}: {
  titulo: string;
  colunas: Coluna[];
  linhas: { chave: string | number; celulas: ReactNode[] }[];
  vazio?: string;
}) {
  return (
    <section className="space-y-2" aria-label={titulo}>
      <h2 className="text-sm font-semibold uppercase tracking-wide text-muted">{titulo}</h2>
      {linhas.length === 0 ? (
        <p className="cartao p-8 text-center text-sm text-muted">{vazio}</p>
      ) : (
        <>
          <ul className="space-y-2 md:hidden print:hidden">
            {linhas.map((l) => (
              <li key={l.chave} className="cartao space-y-1 p-3 text-sm">
                {l.celulas.map((c, i) => (
                  <p key={i} className={`flex justify-between gap-3 ${i === 0 ? "font-medium" : ""}`}>
                    {i > 0 && <span className="text-muted">{colunas[i].titulo}</span>}
                    <span className={`min-w-0 ${i > 0 ? "text-right tabular-nums" : ""}`}>{c}</span>
                  </p>
                ))}
              </li>
            ))}
          </ul>
          <div className="cartao hidden overflow-x-auto md:block print:block">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-border text-xs uppercase tracking-wide text-muted">
                <tr>
                  {colunas.map((c) => (
                    <th key={c.titulo} scope="col" className={`px-4 py-2 font-medium ${c.direita ? "text-right" : ""}`}>{c.titulo}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {linhas.map((l) => (
                  <tr key={l.chave}>
                    {l.celulas.map((c, i) => (
                      <td key={i} className={`px-4 py-2 ${colunas[i]?.direita ? "text-right tabular-nums" : ""}`}>{c}</td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </section>
  );
}
