import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { FornecedorForm } from "@/components/fornecedor-form";
import { podeAdministrarFornecedores } from "@/lib/permissoes";
import { exigirSessao } from "@/lib/session";

export const metadata: Metadata = { title: "Novo fornecedor" };

export default async function NovoFornecedorPage() {
  const sessao = await exigirSessao();
  if (!podeAdministrarFornecedores(sessao.papel)) redirect("/fornecedores");
  return (
    <div className="space-y-6">
      <header>
        <Link href="/fornecedores" className="text-sm text-muted hover:text-foreground">← Fornecedores</Link>
        <h1 className="mt-1 text-2xl font-semibold">Novo fornecedor</h1>
      </header>
      <FornecedorForm />
    </div>
  );
}
