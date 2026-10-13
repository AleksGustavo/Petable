import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { RelatoriosHub } from "@/components/relatorio-filtros";
import { hojeISO } from "@/lib/datas";
import { relatoriosDoPapel } from "@/lib/relatorios";
import { exigirSessao } from "@/lib/session";

export const metadata: Metadata = { title: "Relatórios" };

export default async function RelatoriosPage() {
  const sessao = await exigirSessao();
  const relatorios = relatoriosDoPapel(sessao.papel);
  if (relatorios.length === 0) redirect("/dashboard");
  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl font-semibold">Relatórios</h1>
        <p className="text-sm text-muted">Somente leitura. Escolha um relatório; o período padrão é o mês corrente.</p>
      </header>
      <RelatoriosHub relatorios={relatorios} hoje={hojeISO()} />
    </div>
  );
}
