import Link from "next/link";
import { moeda } from "@/lib/agendamentos";
import type { ResumoDia } from "@/lib/vendas";

/** Card do painel: vendas concluídas hoje (agregado no banco). */
export function DashboardVendas({ resumo }: { resumo: ResumoDia }) {
  return (
    <Link href="/vendas" className="cartao group p-5 transition hover:border-brand">
      <p className="text-sm text-muted">Vendas hoje</p>
      <p className="mt-1 text-4xl font-semibold tabular-nums">{resumo.quantidade}</p>
      <p className="mt-1 text-lg font-medium tabular-nums" data-testid="total-vendas-dia">{moeda(resumo.total)}</p>
      <p className="mt-3 text-sm font-medium text-brand group-hover:underline">Ver vendas →</p>
    </Link>
  );
}
