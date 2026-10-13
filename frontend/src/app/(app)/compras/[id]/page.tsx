import type { Metadata } from "next";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { Aviso } from "@/components/aviso";
import { CompraDetalhes } from "@/components/compra-detalhes";
import { ApiError, apiFetch } from "@/lib/api";
import { hojeISO } from "@/lib/datas";
import type { Compra } from "@/lib/estoque";
import { podeAdministrarCompras, podeVerFornecedoresECompras } from "@/lib/permissoes";
import { exigirSessao } from "@/lib/session";

export const metadata: Metadata = { title: "Compra" };

const AVISOS: Record<string, string> = {
  criada: "Pedido registrado. O estoque será atualizado quando a mercadoria for recebida.",
  recebida: "Recebimento registrado: estoque e custo médio atualizados.",
  cancelada: "Compra cancelada. O estoque não foi alterado.",
};

async function carregar(id: string): Promise<Compra> {
  if (!/^\d+$/.test(id)) notFound();
  try {
    return await apiFetch<Compra>(`/api/v1/compras/${id}`);
  } catch (e) {
    if (e instanceof ApiError && e.problem.status === 404) notFound();
    throw e;
  }
}

export default async function CompraPage(props: PageProps<"/compras/[id]">) {
  const sessao = await exigirSessao();
  if (!podeVerFornecedoresECompras(sessao.papel)) redirect("/dashboard");
  const { id } = await props.params;
  const { ok } = await props.searchParams;
  const compra = await carregar(id);
  const aviso = typeof ok === "string" ? AVISOS[ok] : undefined;

  return (
    <div className="space-y-6">
      <Link href="/compras" className="text-sm text-muted hover:text-foreground">← Compras</Link>
      <header>
        <h1 className="text-2xl font-semibold">Compra #{compra.id}</h1>
        <p className="text-sm text-muted">{compra.fornecedor.nome}</p>
      </header>
      {aviso && <Aviso>{aviso}</Aviso>}
      <CompraDetalhes compra={compra} podeAdministrar={podeAdministrarCompras(sessao.papel)} hoje={hojeISO()} />
    </div>
  );
}
