import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { Aviso } from "@/components/aviso";
import { LancamentoDetalhes } from "@/components/lancamento-detalhes";
import type { Lancamento } from "@/lib/financeiro";
import { carregar } from "@/lib/financeiro-server";
import { podeAdministrarFinanceiro } from "@/lib/permissoes";
import { exigirSessao } from "@/lib/session";

export const metadata: Metadata = { title: "Lançamento" };

const AVISOS: Record<string, string> = {
  criado: "Lançamento registrado no seu caixa.",
  estorno: "Estorno registrado. O lançamento original foi mantido e vinculado a este.",
};

export default async function LancamentoPage(props: PageProps<"/financeiro/lancamentos/[id]">) {
  const sessao = await exigirSessao();
  if (!podeAdministrarFinanceiro(sessao.papel)) redirect("/dashboard");
  const { id } = await props.params;
  const { ok } = await props.searchParams;
  const lancamento = await carregar<Lancamento>("/api/v1/financeiro/lancamentos", id);
  const aviso = typeof ok === "string" ? AVISOS[ok] : undefined;
  return (
    <div className="space-y-6">
      <Link href="/financeiro" className="text-sm text-muted hover:text-foreground">← Financeiro</Link>
      <header>
        <h1 className="text-2xl font-semibold">Lançamento #{lancamento.id}</h1>
        <p className="text-sm text-muted">{lancamento.descricao}</p>
      </header>
      {aviso && <Aviso>{aviso}</Aviso>}
      <LancamentoDetalhes lancamento={lancamento} />
    </div>
  );
}
