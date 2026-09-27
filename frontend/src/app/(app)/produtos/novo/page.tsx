import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { ProdutoForm } from "@/components/produto-form";
import { apiFetch } from "@/lib/api";
import type { CategoriaProduto, FornecedorOpcao } from "@/lib/estoque";
import { podeEditarProdutos } from "@/lib/permissoes";
import { exigirSessao } from "@/lib/session";

export const metadata: Metadata = { title: "Novo produto" };

export default async function NovoProdutoPage() {
  const sessao = await exigirSessao();
  if (!podeEditarProdutos(sessao.papel)) redirect("/produtos");
  const [categorias, fornecedores] = await Promise.all([
    apiFetch<CategoriaProduto[]>("/api/v1/categorias-produto"),
    apiFetch<FornecedorOpcao[]>("/api/v1/fornecedores/opcoes"),
  ]);
  return (
    <div className="space-y-6">
      <header>
        <Link href="/produtos" className="text-sm text-muted hover:text-foreground">← Produtos</Link>
        <h1 className="mt-1 text-2xl font-semibold">Novo produto</h1>
      </header>
      <ProdutoForm categorias={categorias} fornecedores={fornecedores} />
    </div>
  );
}
