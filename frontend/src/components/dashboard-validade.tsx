import Link from "next/link";
import type { AlertasEstoque } from "@/lib/lotes";

/** Card do painel (só com o controle de lote ligado): lotes vencidos e que vencem em breve. Derivado a cada visita. */
export function DashboardValidade({ alertas }: { alertas: AlertasEstoque }) {
  const proximos = alertas.faixas[0];
  const alerta = alertas.vencidos.lotes > 0;
  return (
    <Link href="/estoque/lotes" className={`cartao group p-5 transition hover:border-brand ${alerta ? "border-danger/40" : ""}`}>
      <p className="text-sm text-muted">Validade dos lotes</p>
      <p className={`mt-1 text-4xl font-semibold tabular-nums ${alerta ? "text-danger" : ""}`}>{alertas.vencidos.lotes}</p>
      <p className="text-sm">lote(s) vencido(s) com saldo</p>
      {proximos && (
        <p className="mt-1 text-sm text-muted">
          {proximos.lotes > 0 ? "⚠" : "✓"} {proximos.lotes} vence(m) em até {proximos.ateDias} dias
        </p>
      )}
      <p className="mt-3 text-sm font-medium text-brand group-hover:underline">Ver lotes e alertas →</p>
    </Link>
  );
}
