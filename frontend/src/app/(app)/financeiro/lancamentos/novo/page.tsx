import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { LancamentoForm } from "@/components/lancamento-form";
import { resumoCaixaAtual } from "@/lib/financeiro-server";
import { podeAdministrarFinanceiro } from "@/lib/permissoes";
import { exigirSessao } from "@/lib/session";

export const metadata: Metadata = { title: "Novo lançamento" };

export default async function NovoLancamentoPage() {
  const sessao = await exigirSessao();
  if (!podeAdministrarFinanceiro(sessao.papel)) redirect("/dashboard");
  const caixa = await resumoCaixaAtual();
  return (
    <div className="space-y-6">
      <header>
        <Link href="/financeiro" className="text-sm text-muted hover:text-foreground">← Financeiro</Link>
        <h1 className="mt-1 text-2xl font-semibold">Novo lançamento manual</h1>
        <p className="mt-1 text-sm text-muted">Despesas e outras entradas/saídas que não vêm de venda, serviço ou compra.</p>
      </header>
      <LancamentoForm caixaAberto={caixa.aberto} />
    </div>
  );
}
