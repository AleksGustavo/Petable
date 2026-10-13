import type { Metadata } from "next";
import Link from "next/link";
import { redirect, unstable_rethrow } from "next/navigation";
import { PetForm } from "@/components/pet-form";
import { apiFetch } from "@/lib/api";
import { especiesComRacas } from "@/lib/catalogo";
import type { Cliente } from "@/lib/clientes";
import { podeEditarPets } from "@/lib/permissoes";
import { exigirSessao } from "@/lib/session";

export const metadata: Metadata = { title: "Novo pet" };

/** `?clienteId=` pré-seleciona o tutor, mas só depois de a API confirmar que ele existe e está ativo. */
async function tutorValidado(clienteId: unknown) {
  if (typeof clienteId !== "string" || !/^\d+$/.test(clienteId)) return null;
  try {
    const c = await apiFetch<Cliente>(`/api/v1/clientes/${clienteId}`);
    return { id: c.id, nome: c.nome };
  } catch (e) {
    unstable_rethrow(e);
    return null;
  }
}

export default async function NovoPetPage(props: PageProps<"/pets/novo">) {
  const { clienteId } = await props.searchParams;
  const sessao = await exigirSessao();
  if (!podeEditarPets(sessao.papel)) redirect("/pets");
  const [especies, tutor] = await Promise.all([especiesComRacas(), tutorValidado(clienteId)]);

  return (
    <div className="space-y-6">
      <header>
        <Link href={tutor ? `/clientes/${tutor.id}` : "/pets"} className="text-sm text-muted hover:text-foreground">
          ← {tutor ? tutor.nome : "Pets"}
        </Link>
        <h1 className="mt-1 text-2xl font-semibold">Novo pet</h1>
      </header>
      <PetForm especies={especies} tutorInicial={tutor} />
    </div>
  );
}
