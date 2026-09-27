import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { inativarCliente } from "@/app/actions/clientes";
import { Aviso } from "@/components/aviso";
import { ClientePets } from "@/components/cliente-pets";
import { ClienteForm } from "@/components/cliente-form";
import { ClientePrivacidade } from "@/components/cliente-privacidade";
import { ApiError, apiFetch } from "@/lib/api";
import type { Cliente, Pagina } from "@/lib/clientes";
import { podeAnonimizarClientes, podeEditarClientes, podeEditarPets } from "@/lib/permissoes";
import type { Pet } from "@/lib/pets";
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
  const pets = await apiFetch<Pagina<Pet>>(`/api/v1/pets?clienteId=${cliente.id}&size=100`);
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

      <ClientePets clienteId={cliente.id} pets={pets.content} podeEditar={podeEditarPets(sessao.papel)} />

      {editavel ? (
        <ClienteForm key={cliente.updatedAt} cliente={cliente} />
      ) : (
        <p className="cartao p-4 text-sm text-muted">Seu perfil pode consultar, mas não alterar clientes.</p>
      )}

      {podeAnonimizarClientes(sessao.papel) && <ClientePrivacidade clienteId={cliente.id} />}
    </div>
  );
}
