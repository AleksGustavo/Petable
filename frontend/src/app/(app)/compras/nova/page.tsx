import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { CompraForm } from "@/components/compra-form";
import { apiFetch } from "@/lib/api";
import { hojeISO } from "@/lib/datas";
import type { FornecedorOpcao } from "@/lib/estoque";
import { podeAdministrarCompras } from "@/lib/permissoes";
import { exigirSessao } from "@/lib/session";
import type { FuncionarioResumo } from "@/lib/usuarios";

export const metadata: Metadata = { title: "Nova compra" };

export default async function NovaCompraPage() {
  const sessao = await exigirSessao();
  if (!podeAdministrarCompras(sessao.papel)) redirect("/compras");
  const [fornecedores, funcionarios] = await Promise.all([
    apiFetch<FornecedorOpcao[]>("/api/v1/fornecedores/opcoes"),
    apiFetch<FuncionarioResumo[]>("/api/v1/funcionarios"),
  ]);
  return (
    <div className="space-y-6">
      <header>
        <Link href="/compras" className="text-sm text-muted hover:text-foreground">← Compras</Link>
        <h1 className="mt-1 text-2xl font-semibold">Nova compra</h1>
        <p className="mt-1 text-sm text-muted">Registre o pedido feito ao fornecedor. O estoque só entra no recebimento.</p>
      </header>
      <CompraForm fornecedores={fornecedores} funcionarios={funcionarios} hoje={hojeISO()} />
    </div>
  );
}
