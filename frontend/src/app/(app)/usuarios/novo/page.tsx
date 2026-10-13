import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { NovoUsuarioForm } from "@/components/novo-usuario-form";
import { podeAdministrarUsuarios } from "@/lib/permissoes";
import { exigirSessao } from "@/lib/session";

export const metadata: Metadata = { title: "Novo usuário" };

export default async function NovoUsuarioPage() {
  const sessao = await exigirSessao();
  if (!podeAdministrarUsuarios(sessao.papel)) redirect("/dashboard");
  return (
    <div className="space-y-6">
      <header>
        <Link href="/usuarios" className="text-sm text-muted hover:text-foreground">← Usuários</Link>
        <h1 className="mt-1 text-2xl font-semibold">Novo usuário</h1>
        <p className="mt-1 text-sm text-muted">O perfil de funcionário é opcional e pode ser cadastrado depois, na ficha do usuário.</p>
      </header>
      <NovoUsuarioForm />
    </div>
  );
}
