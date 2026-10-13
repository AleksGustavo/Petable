import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { PdvCliente } from "@/components/pdv-cliente";
import { resumoCaixaAtual } from "@/lib/financeiro-server";
import { podeVender } from "@/lib/permissoes";
import { exigirSessao } from "@/lib/session";

export const metadata: Metadata = { title: "PDV" };

export default async function PdvPage() {
  const sessao = await exigirSessao();
  if (!podeVender(sessao.papel)) redirect("/dashboard");
  const caixa = await resumoCaixaAtual();
  return (
    <div className="space-y-4">
      <header className="flex flex-wrap items-end justify-between gap-2">
        <div>
          <h1 className="text-2xl font-semibold">Frente de caixa</h1>
          <p className="text-sm text-muted">Preços, desconto e total são confirmados pelo sistema antes de finalizar.</p>
        </div>
        <Link href="/vendas" className="text-sm font-medium text-brand hover:underline">Vendas de hoje →</Link>
      </header>
      <PdvCliente papel={sessao.papel} caixaAberto={caixa.aberto} />
    </div>
  );
}
