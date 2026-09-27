import type { Metadata } from "next";
import Link from "next/link";
import { redirect, unstable_rethrow } from "next/navigation";
import { AgendamentoForm, type PetInicial } from "@/components/agendamento-form";
import type { Servico } from "@/lib/agendamentos";
import { apiFetch } from "@/lib/api";
import { podeAgendar } from "@/lib/permissoes";
import type { Pet } from "@/lib/pets";
import type { FuncionarioResumo } from "@/lib/usuarios";
import { exigirSessao } from "@/lib/session";

export const metadata: Metadata = { title: "Novo agendamento" };

/** `?petId=` só pré-seleciona depois de a API confirmar que o pet existe e está ativo. */
async function petValidado(petId: unknown): Promise<PetInicial | null> {
  if (typeof petId !== "string" || !/^\d+$/.test(petId)) return null;
  try {
    const p = await apiFetch<Pet>(`/api/v1/pets/${petId}`);
    return { id: p.id, nome: p.nome, tutor: { id: p.tutor.id, nome: p.tutor.nome } };
  } catch (e) {
    unstable_rethrow(e);
    return null;
  }
}

export default async function NovoAgendamentoPage(props: PageProps<"/agendamentos/novo">) {
  const { petId } = await props.searchParams;
  const sessao = await exigirSessao();
  if (!podeAgendar(sessao.papel)) redirect("/agendamentos");
  const [servicos, pet, funcionarios] = await Promise.all([
    apiFetch<Servico[]>("/api/v1/servicos?ativo=true"),
    petValidado(petId),
    apiFetch<FuncionarioResumo[]>("/api/v1/funcionarios"),
  ]);

  return (
    <div className="space-y-6">
      <header>
        <Link href={pet ? `/pets/${pet.id}` : "/agendamentos"} className="text-sm text-muted hover:text-foreground">
          ← {pet ? pet.nome : "Agendamentos"}
        </Link>
        <h1 className="mt-1 text-2xl font-semibold">Novo agendamento</h1>
      </header>
      <AgendamentoForm servicos={servicos} petInicial={pet} funcionarios={funcionarios} />
    </div>
  );
}
