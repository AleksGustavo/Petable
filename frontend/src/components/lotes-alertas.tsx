import Link from "next/link";
import { rotuloFaixa, type AlertasEstoque } from "@/lib/lotes";

const base = "cartao block p-4 transition hover:border-brand";

/**
 * Alertas de estoque calculados na hora pela API a partir dos lotes reais (nada é gravado nem enviado, então nunca
 * repetem). Cada cartão leva à lista já filtrada. Ícone + texto, nunca só cor.
 */
export function LotesAlertas({ alertas }: { alertas: AlertasEstoque }) {
  return (
    <section aria-label="Alertas de validade e estoque" className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
      <Link href="/estoque/lotes?situacao=vencidos" className={`${base} ${alertas.vencidos.lotes > 0 ? "border-danger/40" : ""}`}>
        <p className="text-sm text-muted">{alertas.vencidos.lotes > 0 ? "✕" : "✓"} Lotes vencidos com saldo</p>
        <p className={`mt-1 text-3xl font-semibold tabular-nums ${alertas.vencidos.lotes > 0 ? "text-danger" : ""}`}>{alertas.vencidos.lotes}</p>
        <p className="text-xs text-muted">{alertas.vencidos.unidades} unidade(s) a baixar</p>
      </Link>
      {alertas.faixas.map((f) => (
        <Link key={f.ateDias} href={`/estoque/lotes?situacao=${f.ateDias}`} className={`${base} ${f.lotes > 0 ? "border-amber-500/40" : ""}`}>
          <p className="text-sm text-muted">{f.lotes > 0 ? "⚠" : "✓"} {rotuloFaixa(f)}</p>
          <p className="mt-1 text-3xl font-semibold tabular-nums">{f.lotes}</p>
          <p className="text-xs text-muted">{f.unidades} unidade(s)</p>
        </Link>
      ))}
      <Link href="/estoque/lotes?situacao=bloqueados" className={`${base} ${alertas.bloqueados.lotes > 0 ? "border-amber-500/40" : ""}`}>
        <p className="text-sm text-muted">{alertas.bloqueados.lotes > 0 ? "⛔" : "✓"} Lotes bloqueados com saldo</p>
        <p className="mt-1 text-3xl font-semibold tabular-nums">{alertas.bloqueados.lotes}</p>
        <p className="text-xs text-muted">{alertas.bloqueados.unidades} unidade(s)</p>
      </Link>
      <Link href="/produtos?abaixoMinimo=true&ativo=true" className={`${base} ${alertas.produtosAbaixoDoMinimo > 0 ? "border-danger/40" : ""}`}>
        <p className="text-sm text-muted">{alertas.produtosAbaixoDoMinimo > 0 ? "⚠" : "✓"} Produtos abaixo do mínimo</p>
        <p className="mt-1 text-3xl font-semibold tabular-nums">{alertas.produtosAbaixoDoMinimo}</p>
      </Link>
      <Link href="/produtos?ativo=true" className={`${base} ${alertas.produtosSemControle > 0 ? "border-border" : ""}`}>
        <p className="text-sm text-muted">○ Produtos com saldo e sem controle de lote</p>
        <p className="mt-1 text-3xl font-semibold tabular-nums">{alertas.produtosSemControle}</p>
        <p className="text-xs text-muted">Só informativo: nem todo produto precisa de lote.</p>
      </Link>
    </section>
  );
}
