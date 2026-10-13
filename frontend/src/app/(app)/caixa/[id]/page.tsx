import type { Metadata } from "next";
import Link from "next/link";
import { Aviso } from "@/components/aviso";
import { CaixaDetalhes } from "@/components/caixa";
import { BadgeCaixa } from "@/components/financeiro-badges";
import type { Caixa } from "@/lib/financeiro";
import { carregar } from "@/lib/financeiro-server";
import { exigirSessao } from "@/lib/session";

export const metadata: Metadata = { title: "Caixa" };

/** ADMIN vê qualquer caixa; os demais, só o próprio (a API responde 403). */
export default async function CaixaDetalhePage(props: PageProps<"/caixa/[id]">) {
  await exigirSessao();
  const { id } = await props.params;
  const { ok } = await props.searchParams;
  const caixa = await carregar<Caixa>("/api/v1/caixas", id);
  return (
    <div className="space-y-6">
      <Link href="/caixa" className="text-sm text-muted hover:text-foreground">← Caixa</Link>
      <header className="flex flex-wrap items-end justify-between gap-2">
        <h1 className="text-2xl font-semibold">Caixa #{caixa.id}</h1>
        <BadgeCaixa aberto={caixa.status === "ABERTO"} />
      </header>
      {ok === "fechado" && <Aviso>Caixa fechado. O fechamento é definitivo; imprima o comprovante se precisar.</Aviso>}
      <CaixaDetalhes caixa={caixa} />
    </div>
  );
}
