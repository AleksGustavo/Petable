import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { Comprovante } from "@/components/comprovante";
import { podeVender } from "@/lib/permissoes";
import { exigirSessao } from "@/lib/session";
import { carregarVenda } from "@/lib/vendas-server";

export const metadata: Metadata = { title: "Comprovante" };

export default async function ComprovantePage(props: PageProps<"/vendas/[id]/comprovante">) {
  const sessao = await exigirSessao();
  if (!podeVender(sessao.papel)) redirect("/dashboard");
  const { id } = await props.params;
  const venda = await carregarVenda(id);
  return (
    <>
      <style>{"@media print { @page { size: 80mm auto; margin: 0; } body { background: #fff; } }"}</style>
      <Comprovante venda={venda} />
    </>
  );
}
