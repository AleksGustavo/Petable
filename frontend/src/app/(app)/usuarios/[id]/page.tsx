import type { Metadata } from "next";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { atualizarContaUsuario, salvarFuncionario } from "@/app/actions/usuarios";
import { Aviso } from "@/components/aviso";
import { ContaForm } from "@/components/conta-form";
import { FuncionarioForm } from "@/components/funcionario-form";
import { UsuarioSeguranca } from "@/components/usuario-seguranca";
import { ApiError, apiFetch } from "@/lib/api";
import { podeAdministrarUsuarios } from "@/lib/permissoes";
import { exigirSessao } from "@/lib/session";
import type { Funcionario, Usuario } from "@/lib/usuarios";

export const metadata: Metadata = { title: "Usuário" };

const AVISOS: Record<string, string> = {
  criado: "Usuário criado.",
  conta: "Conta atualizada.",
  papel: "Papel alterado. As sessões abertas deste usuário foram encerradas.",
  desativado: "Usuário desativado. Ele não consegue mais entrar; agendamentos existentes foram mantidos.",
  ativado: "Usuário reativado.",
  funcionario: "Dados do funcionário salvos.",
};

async function carregar(id: string): Promise<Usuario> {
  if (!/^\d+$/.test(id)) notFound();
  try {
    return await apiFetch<Usuario>(`/api/v1/usuarios/${id}`);
  } catch (e) {
    if (e instanceof ApiError && e.problem.status === 404) notFound();
    throw e;
  }
}

export default async function UsuarioPage(props: PageProps<"/usuarios/[id]">) {
  const sessao = await exigirSessao();
  if (!podeAdministrarUsuarios(sessao.papel)) redirect("/dashboard");
  const { id } = await props.params;
  const { ok } = await props.searchParams;
  const usuario = await carregar(id);
  const funcionario = usuario.funcionario ? await apiFetch<Funcionario>(`/api/v1/funcionarios/${usuario.funcionario.id}`) : null;
  const aviso = typeof ok === "string" ? AVISOS[ok] : undefined;

  return (
    <div className="space-y-6">
      <Link href="/usuarios" className="text-sm text-muted hover:text-foreground">← Usuários</Link>
      <header className="flex flex-wrap items-end justify-between gap-2">
        <div>
          <h1 className="text-2xl font-semibold">{usuario.username}</h1>
          <p className="text-sm text-muted">{usuario.email}</p>
        </div>
        {!usuario.ativo && <span className="rounded-full border border-border px-3 py-1 text-sm">○ Inativo</span>}
      </header>
      {aviso && <Aviso>{aviso}</Aviso>}

      <section className="cartao space-y-4 p-4 sm:p-6" aria-labelledby="sec-conta">
        <h2 id="sec-conta" className="text-sm font-semibold text-muted">Conta</h2>
        <ContaForm key={`${usuario.username}-${usuario.email}`} action={atualizarContaUsuario.bind(null, usuario.id)} username={usuario.username} email={usuario.email} />
      </section>

      <section className="cartao space-y-4 p-4 sm:p-6" aria-labelledby="sec-funcionario">
        <h2 id="sec-funcionario" className="text-sm font-semibold text-muted">Funcionário</h2>
        {funcionario ? (
          <>
            {!funcionario.disponivel && (
              <p className="text-sm text-muted">Conta inativa: este funcionário não aparece em novos agendamentos.</p>
            )}
            <FuncionarioForm action={salvarFuncionario.bind(null, usuario.id, funcionario.id)} funcionario={funcionario} modo="admin" />
          </>
        ) : (
          <details>
            <summary className="botao-secundario cursor-pointer list-none">Cadastrar funcionário</summary>
            <div className="mt-4">
              <FuncionarioForm action={salvarFuncionario.bind(null, usuario.id, null)} modo="admin" />
            </div>
          </details>
        )}
      </section>

      <section className="cartao space-y-4 p-4 sm:p-6" aria-labelledby="sec-seguranca">
        <h2 id="sec-seguranca" className="text-sm font-semibold text-muted">Segurança</h2>
        <UsuarioSeguranca usuario={usuario} ehVoceMesmo={usuario.id === sessao.id} />
      </section>
    </div>
  );
}
