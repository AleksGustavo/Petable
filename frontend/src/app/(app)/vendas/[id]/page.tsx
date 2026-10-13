import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { Aviso } from "@/components/aviso";
import { VendaDetalhes } from "@/components/venda-detalhes";
import { podeCancelarVendas, podeVender } from "@/lib/permissoes";
import { exigirSessao } from "@/lib/session";
import { dentroDoPrazoDeCancelamento } from "@/lib/vendas";
import { carregarVenda } from "@/lib/vendas-server";

export const metadata: Metadata = { title: "Venda" };

export default async function VendaPage(props: PageProps<"/vendas/[id]">) {
  const sessao = await exigirSessao();
  if (!podeVender(sessao.papel)) redirect("/dashboard");
  const { id } = await props.params;
  const { ok } = await props.searchParams;
  const venda = await carregarVenda(id);
  // O botão só aparece dentro do prazo; a API confere de novo (409 fora do prazo).
  const podeCancelar = podeCancelarVendas(sessao.papel) && dentroDoPrazoDeCancelamento(venda.dataVenda);

  return (
    <div className="space-y-6">
      <Link href="/vendas" className="text-sm text-muted hover:text-foreground">← Vendas</Link>
      <header>
        <h1 className="text-2xl font-semibold">Venda #{venda.id}</h1>
      </header>
      {ok === "cancelada" && <Aviso>Venda cancelada. O estoque dos itens foi estornado.</Aviso>}
      <VendaDetalhes venda={venda} podeCancelar={podeCancelar} />
    </div>
  );
}
