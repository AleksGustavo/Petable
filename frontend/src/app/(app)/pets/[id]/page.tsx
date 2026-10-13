import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { desativarPet, removerVacina } from "@/app/actions/pets";
import { Aviso } from "@/components/aviso";
import { BotaoConfirmar } from "@/components/botao-confirmar";
import { ConsultaForm } from "@/components/consulta-form";
import { FotoPet } from "@/components/foto-pet";
import { PetForm } from "@/components/pet-form";
import { ProximosAgendamentos } from "@/components/proximos-agendamentos";
import { StatusVacinaBadge } from "@/components/status-vacina-badge";
import { VacinaForm } from "@/components/vacina-form";
import type { Agendamento } from "@/lib/agendamentos";
import { ApiError, apiFetch } from "@/lib/api";
import { especiesComRacas } from "@/lib/catalogo";
import { formatarDataHora } from "@/lib/datas";
import { formatarData, mascaraTelefone } from "@/lib/format";
import { podeAgendar, podeEditarPets } from "@/lib/permissoes";
import { rotuloPorte, type Consulta, type LancamentoVacina, type Pet, type VacinaCatalogo } from "@/lib/pets";
import { exigirSessao } from "@/lib/session";

export const metadata: Metadata = { title: "Pet" };

const ABAS = [
  { id: "dados", rotulo: "Dados" },
  { id: "vacinas", rotulo: "Vacinas" },
  { id: "consultas", rotulo: "Consultas" },
] as const;
type Aba = (typeof ABAS)[number]["id"];

const MENSAGENS: Record<string, string> = {
  vacina: "Vacina registrada.",
  removida: "Lançamento removido da carteira.",
  consulta: "Consulta registrada.",
};

async function carregar(id: string): Promise<Pet> {
  if (!/^\d+$/.test(id)) notFound();
  try {
    return await apiFetch<Pet>(`/api/v1/pets/${id}`);
  } catch (e) {
    if (e instanceof ApiError && e.problem.status === 404) notFound();
    throw e;
  }
}

function Info({ rotulo, valor }: { rotulo: string; valor: React.ReactNode }) {
  return (
    <div>
      <dt className="text-xs uppercase tracking-wide text-muted">{rotulo}</dt>
      <dd className="mt-0.5 text-sm font-medium">{valor}</dd>
    </div>
  );
}

export default async function PetPage(props: PageProps<"/pets/[id]">) {
  const { id } = await props.params;
  const sp = await props.searchParams;
  const aba: Aba = ABAS.some((a) => a.id === sp.aba) ? (sp.aba as Aba) : "dados";
  const [sessao, pet] = await Promise.all([exigirSessao(), carregar(id)]);
  const editavel = podeEditarPets(sessao.papel);

  const [proximos, especies, carteira, catalogoVacinas, consultas] = await Promise.all([
    apiFetch<Agendamento[]>(`/api/v1/pets/${pet.id}/agendamentos/proximos?limit=5`),
    aba === "dados" && editavel ? especiesComRacas() : Promise.resolve([]),
    aba === "vacinas" ? apiFetch<LancamentoVacina[]>(`/api/v1/pets/${pet.id}/vacinas`) : Promise.resolve([]),
    aba === "vacinas" && editavel
      ? apiFetch<VacinaCatalogo[]>(`/api/v1/especies/${pet.especie.id}/vacinas`)
      : Promise.resolve([]),
    aba === "consultas" ? apiFetch<Consulta[]>(`/api/v1/pets/${pet.id}/consultas`) : Promise.resolve([]),
  ]);

  const ok = typeof sp.ok === "string" ? MENSAGENS[sp.ok] : undefined;

  return (
    <div className="space-y-6">
      <Link href="/pets" className="text-sm text-muted hover:text-foreground">← Pets</Link>

      <header className="cartao flex flex-col gap-4 p-4 sm:flex-row sm:items-center sm:p-6">
        <FotoPet pet={pet} tamanho={96} />
        <div className="min-w-0 flex-1">
          <h1 className="truncate text-2xl font-semibold">{pet.nome}</h1>
          <p className="text-sm text-muted">
            {pet.especie.nome}
            {pet.raca && ` · ${pet.raca.nome}`}
          </p>
          <p className="mt-1 text-sm">
            Tutor:{" "}
            <Link href={`/clientes/${pet.tutor.id}`} className="font-medium text-brand hover:underline">
              {pet.tutor.nome}
            </Link>{" "}
            <span className="text-muted">· {mascaraTelefone(pet.tutor.telefone)}</span>
          </p>
          <ul className="mt-3 flex flex-wrap gap-2 text-xs" aria-label="Resumo">
            <li className="rounded-full bg-brand-soft px-2.5 py-1 text-brand-strong">{pet.idade?.descricao ?? "Idade não informada"}</li>
            {pet.peso != null && <li className="rounded-full border border-border px-2.5 py-1">{String(pet.peso).replace(".", ",")} kg</li>}
            {pet.porte && <li className="rounded-full border border-border px-2.5 py-1">Porte {rotuloPorte(pet.porte).toLowerCase()}</li>}
            <li className="rounded-full border border-border px-2.5 py-1">{pet.castrado ? "Castrado(a)" : "Não castrado(a)"}</li>
          </ul>
        </div>
        {editavel && (
          <BotaoConfirmar
            action={desativarPet.bind(null, pet.id, pet.tutor.id)}
            rotulo="Remover pet"
            confirmacao={`Remover ${pet.nome} da lista de pets ativos? A carteira e as consultas continuam guardadas.`}
            className="botao-perigo w-full sm:w-auto"
          />
        )}
      </header>

      <ProximosAgendamentos petId={pet.id} agendamentos={proximos} podeAgendar={podeAgendar(sessao.papel)} />

      {sp.salvo && <Aviso>Dados do pet salvos.</Aviso>}
      {sp.foto === "erro" && <Aviso tipo="erro">O pet foi salvo, mas a foto não pôde ser enviada. Tente de novo em “Dados”.</Aviso>}
      {ok && <Aviso>{ok}</Aviso>}

      <nav aria-label="Seções do pet" className="flex gap-1 rounded-xl border border-border bg-surface p-1">
        {ABAS.map((a) => (
          <Link
            key={a.id}
            href={`/pets/${pet.id}?aba=${a.id}`}
            aria-current={aba === a.id ? "page" : undefined}
            className={`flex-1 rounded-lg px-3 py-2 text-center text-sm font-medium transition ${
              aba === a.id ? "bg-brand text-white" : "text-muted hover:bg-brand-soft"
            }`}
          >
            {a.rotulo}
          </Link>
        ))}
      </nav>

      {aba === "dados" &&
        (editavel ? (
          <PetForm key={pet.updatedAt} pet={pet} especies={especies} />
        ) : (
          <dl className="cartao grid gap-4 p-4 sm:grid-cols-3 sm:p-6">
            <Info rotulo="Nascimento" valor={formatarData(pet.dataNascimento)} />
            <Info rotulo="Cor" valor={pet.cor ?? "—"} />
            <Info rotulo="Porte" valor={rotuloPorte(pet.porte)} />
            <div className="sm:col-span-3">
              <Info rotulo="Observações" valor={pet.observacoes ?? "—"} />
            </div>
          </dl>
        ))}

      {aba === "vacinas" && (
        <section className="space-y-4" aria-label="Carteira de vacinação">
          {editavel && (
            <details className="cartao group p-4 sm:p-6" open={Boolean(sp.registrar)}>
              <summary className="botao-primario w-full cursor-pointer list-none sm:w-auto">Registrar vacina</summary>
              <div className="mt-4">
                <VacinaForm petId={pet.id} vacinas={catalogoVacinas} />
              </div>
            </details>
          )}
          {carteira.length === 0 ? (
            <p className="cartao p-8 text-center text-sm text-muted">Nenhuma vacina registrada na carteira.</p>
          ) : (
            <ul className="space-y-3">
              {carteira.map((l) => (
                <li key={l.id} className="cartao flex flex-col gap-3 p-4 sm:flex-row sm:items-start sm:justify-between">
                  <div className="min-w-0 space-y-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="font-medium">{l.vacina.nome}</p>
                      <StatusVacinaBadge status={l.status} />
                    </div>
                    <p className="text-sm text-muted">
                      Aplicada em {formatarData(l.dataAplicacao)} · Próxima dose: {formatarData(l.dataProxima)}
                    </p>
                    {(l.veterinario || l.lote) && (
                      <p className="text-sm text-muted">
                        {l.veterinario && `Veterinário: ${l.veterinario}`}
                        {l.veterinario && l.lote && " · "}
                        {l.lote && `Lote: ${l.lote}`}
                      </p>
                    )}
                    {l.observacoes && <p className="text-sm">{l.observacoes}</p>}
                  </div>
                  {editavel && (
                    <BotaoConfirmar
                      action={removerVacina.bind(null, pet.id, l.id)}
                      rotulo="Remover"
                      confirmacao={`Remover o lançamento de ${l.vacina.nome} de ${formatarData(l.dataAplicacao)}?`}
                      className="botao-perigo w-full px-3 py-1.5 sm:w-auto"
                    />
                  )}
                </li>
              ))}
            </ul>
          )}
        </section>
      )}

      {aba === "consultas" && (
        <section className="space-y-4" aria-label="Histórico de consultas">
          <details className="cartao p-4 sm:p-6">
            <summary className="botao-primario w-full cursor-pointer list-none sm:w-auto">Registrar consulta</summary>
            <div className="mt-4">
              <ConsultaForm petId={pet.id} />
            </div>
          </details>
          {consultas.length === 0 ? (
            <p className="cartao p-8 text-center text-sm text-muted">Nenhuma consulta registrada.</p>
          ) : (
            <ol className="space-y-3">
              {consultas.map((c) => (
                <li key={c.id} className="cartao space-y-2 p-4">
                  <div className="flex flex-wrap items-baseline justify-between gap-2">
                    <p className="font-medium">{formatarDataHora(c.dataConsulta)}</p>
                    <p className="text-sm text-muted">{c.veterinario}</p>
                  </div>
                  <dl className="grid gap-2 text-sm sm:grid-cols-2">
                    {c.motivo && <Info rotulo="Motivo" valor={c.motivo} />}
                    {c.diagnostico && <Info rotulo="Diagnóstico" valor={c.diagnostico} />}
                    {c.tratamento && <Info rotulo="Tratamento" valor={c.tratamento} />}
                    {c.observacoes && <Info rotulo="Observações" valor={c.observacoes} />}
                  </dl>
                </li>
              ))}
            </ol>
          )}
        </section>
      )}
    </div>
  );
}
