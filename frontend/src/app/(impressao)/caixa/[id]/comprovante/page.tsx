import type { Metadata } from "next";
import { ComprovanteCaixa } from "@/components/caixa";
import type { Caixa } from "@/lib/financeiro";
import { carregar } from "@/lib/financeiro-server";
import { exigirSessao } from "@/lib/session";

export const metadata: Metadata = { title: "Comprovante de caixa" };

export default async function ComprovanteCaixaPage(props: PageProps<"/caixa/[id]/comprovante">) {
  await exigirSessao();
  const { id } = await props.params;
  const caixa = await carregar<Caixa>("/api/v1/caixas", id);
  return (
    <>
      <style>{"@media print { @page { size: 80mm auto; margin: 0; } body { background: #fff; } }"}</style>
      <ComprovanteCaixa caixa={caixa} />
    </>
  );
}
