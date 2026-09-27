import Link from "next/link";

/** Card do painel: produtos ativos com saldo igual ou abaixo do mínimo (contado na API). */
export function DashboardEstoque({ produtosAbaixoDoMinimo }: { produtosAbaixoDoMinimo: number }) {
  const alerta = produtosAbaixoDoMinimo > 0;
  return (
    <Link
      href="/produtos?abaixoMinimo=true&ativo=true"
      className={`cartao group p-5 transition hover:border-brand ${alerta ? "border-danger/40" : ""}`}
    >
      <p className="text-sm text-muted">Produtos abaixo do estoque mínimo</p>
      <p className={`mt-1 text-4xl font-semibold tabular-nums ${alerta ? "text-danger" : ""}`}>{produtosAbaixoDoMinimo}</p>
      <p className="mt-3 text-sm font-medium text-brand group-hover:underline">
        {alerta ? "⚠ Ver produtos para repor →" : "✓ Estoque em dia · ver produtos →"}
      </p>
    </Link>
  );
}
