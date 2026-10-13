import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { ServicoForm } from "@/components/servico-form";
import { podeAdministrarServicos } from "@/lib/permissoes";
import { exigirSessao } from "@/lib/session";

export const metadata: Metadata = { title: "Novo serviço" };

export default async function NovoServicoPage() {
  const sessao = await exigirSessao();
  if (!podeAdministrarServicos(sessao.papel)) redirect("/servicos");
  return (
    <div className="space-y-6">
      <header>
        <Link href="/servicos" className="text-sm text-muted hover:text-foreground">← Serviços</Link>
        <h1 className="mt-1 text-2xl font-semibold">Novo serviço</h1>
        <p className="mt-1 text-sm text-muted">
          Para habilitar o fluxo de vacinação com lançamento na carteira, cadastre um serviço chamado exatamente “Vacinação”.
        </p>
      </header>
      <ServicoForm />
    </div>
  );
}
