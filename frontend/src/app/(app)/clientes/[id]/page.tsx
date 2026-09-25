import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { inativarCliente } from "@/app/actions/clientes";
import { Aviso } from "@/components/aviso";
import { ClienteForm } from "@/components/cliente-form";
import { ApiError, apiFetch } from "@/lib/api";
import type { Cliente } from "@/lib/clientes";
import { podeEditarClientes } from "@/lib/permissoes";
import { exigirSessao } from "@/lib/session";

export const metadata: Metadata = { title: "Cliente" };

async function carregar(id: string): Promise<Cliente> {
  if (!/^\d+$/.test(id)) notFound();
  try {
    return await apiFetch<Cliente>(`/api/v1/clientes/${id}`);
  } catch (e) {
    if (e instanceof ApiError && e.problem.status === 404) notFound();
    throw e;
  }
}

export default async function ClientePage(props: PageProps<"/clientes/[id]">) {
  const { id } = await props.params;
  const { salvo } = await props.searchParams;
  const [sessao, cliente] = await Promise.all([exigirSessao(), carregar(id)]);
  const editavel = podeEditarClientes(sessao.papel);

  return (
    <div className="space-y-6">
      <header className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <Link href="/clientes" className="text-sm text-muted hover:text-foreground">← Clientes</Link>
          <h1 className="mt-1 text-2xl font-semibold">{cliente.nome}</h1>
        </div>
        {editavel && (
          <form action={inativarCliente.bind(null, cliente.id)}>
            <button type="submit" className="botao-perigo w-full sm:w-auto">Inativar cliente</button>
          </form>
        )}
      </header>

      {salvo && <Aviso>Dados salvos.</Aviso>}

      {editavel ? (
        <ClienteForm key={cliente.updatedAt} cliente={cliente} />
      ) : (
        <p className="cartao p-4 text-sm text-muted">Seu perfil pode consultar, mas não alterar clientes.</p>
      )}
    </div>
  );
}
