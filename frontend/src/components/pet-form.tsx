"use client";

import Link from "next/link";
import { useActionState, useState } from "react";
import { salvarPet } from "@/app/actions/pets";
import { Campo } from "@/components/campo";
import { TutorPicker } from "@/components/tutor-picker";
import type { FormState } from "@/lib/clientes";
import { hojeISO } from "@/lib/datas";
import { FOTO_MAX_BYTES, FOTO_TIPOS, PORTES, type EspecieComRacas, type Pet } from "@/lib/pets";

type Props = {
  pet?: Pet;
  especies: EspecieComRacas[];
  tutorInicial?: { id: number; nome: string } | null;
};

export function PetForm({ pet, especies, tutorInicial }: Props) {
  const acao = salvarPet.bind(null, pet?.id ?? null);
  const [state, action, pending] = useActionState<FormState, FormData>(acao, {});
  const erros = state.erros ?? {};
  const [especieId, setEspecieId] = useState(pet ? String(pet.especie.id) : "");
  const [racaId, setRacaId] = useState(pet?.raca ? String(pet.raca.id) : "");
  const [erroFoto, setErroFoto] = useState<string | null>(null);
  const racas = especies.find((e) => String(e.id) === especieId)?.racas ?? [];
  const valor = (campo: string, padrao: string) => state.valores?.[campo] ?? padrao;

  return (
    <form action={action} className="space-y-6" noValidate>
      {state.mensagem && (
        <p role="alert" className="rounded-lg bg-danger-soft px-4 py-3 text-sm text-danger">
          {state.mensagem}
        </p>
      )}

      <fieldset className="cartao grid gap-4 p-4 sm:grid-cols-2 sm:p-6">
        <legend className="px-1 text-sm font-semibold text-muted">Identificação</legend>
        <div className="sm:col-span-2">
          <TutorPicker inicial={pet?.tutor ?? tutorInicial} erro={erros.clienteId} />
        </div>
        <Campo className="sm:col-span-2" rotulo="Nome do pet" name="nome" required maxLength={50} defaultValue={valor("nome", pet?.nome ?? "")} erro={erros.nome} />

        <div>
          <label htmlFor="campo-especie" className="mb-1 block text-sm font-medium">
            Espécie<span className="text-danger"> *</span>
          </label>
          <select
            id="campo-especie"
            name="especieId"
            className="campo"
            value={especieId}
            onChange={(e) => {
              setEspecieId(e.target.value);
              setRacaId("");
            }}
            aria-invalid={erros.especieId ? true : undefined}
          >
            <option value="">Selecione…</option>
            {especies.map((e) => (
              <option key={e.id} value={e.id}>{e.nome}</option>
            ))}
          </select>
          {erros.especieId && <p className="mt-1 text-xs text-danger">{erros.especieId}</p>}
        </div>

        <div>
          <label htmlFor="campo-raca" className="mb-1 block text-sm font-medium">Raça</label>
          <select
            id="campo-raca"
            name="racaId"
            className="campo"
            value={racaId}
            onChange={(e) => setRacaId(e.target.value)}
            disabled={!especieId}
            aria-invalid={erros.racaId ? true : undefined}
          >
            <option value="">{especieId ? "Não informada" : "Escolha a espécie primeiro"}</option>
            {racas.map((r) => (
              <option key={r.id} value={r.id}>{r.nome}</option>
            ))}
          </select>
          {erros.racaId && <p className="mt-1 text-xs text-danger">{erros.racaId}</p>}
        </div>
      </fieldset>

      <fieldset className="cartao grid gap-4 p-4 sm:grid-cols-3 sm:p-6">
        <legend className="px-1 text-sm font-semibold text-muted">Características</legend>
        <Campo rotulo="Nascimento" name="dataNascimento" type="date" max={hojeISO()} defaultValue={valor("dataNascimento", pet?.dataNascimento ?? "")} erro={erros.dataNascimento} />
        <Campo rotulo="Peso (kg)" name="peso" inputMode="decimal" placeholder="Ex.: 12,5" defaultValue={valor("peso", pet?.peso != null ? String(pet.peso).replace(".", ",") : "")} erro={erros.peso} />
        <Campo rotulo="Cor" name="cor" maxLength={30} defaultValue={valor("cor", pet?.cor ?? "")} erro={erros.cor} />
        <div>
          <label htmlFor="campo-porte" className="mb-1 block text-sm font-medium">Porte</label>
          <select id="campo-porte" name="porte" className="campo" defaultValue={valor("porte", pet?.porte ?? "")}>
            <option value="">Não informado</option>
            {PORTES.map((p) => (
              <option key={p.valor} value={p.valor}>{p.rotulo}</option>
            ))}
          </select>
          {erros.porte && <p className="mt-1 text-xs text-danger">{erros.porte}</p>}
        </div>
        <label className="flex items-center gap-2 self-end pb-2 text-sm font-medium sm:col-span-2">
          <input type="checkbox" name="castrado" defaultChecked={state.valores ? state.valores.castrado === "on" : pet?.castrado} className="size-4 accent-[var(--brand)]" />
          Castrado(a)
        </label>
      </fieldset>

      <fieldset className="cartao grid gap-4 p-4 sm:p-6">
        <legend className="px-1 text-sm font-semibold text-muted">Foto e observações</legend>
        <div>
          <label htmlFor="campo-foto" className="mb-1 block text-sm font-medium">{pet?.fotoUrl ? "Trocar foto" : "Foto"}</label>
          <input
            id="campo-foto"
            name="foto"
            type="file"
            accept={FOTO_TIPOS.join(",")}
            className="block w-full text-sm file:mr-3 file:rounded-lg file:border-0 file:bg-brand-soft file:px-3 file:py-2 file:text-sm file:font-medium file:text-brand-strong"
            aria-describedby="campo-foto-ajuda"
            onChange={(e) => {
              const f = e.target.files?.[0];
              setErroFoto(f && f.size > FOTO_MAX_BYTES ? "A foto deve ter no máximo 5 MB." : null);
            }}
          />
          <p id="campo-foto-ajuda" className={`mt-1 text-xs ${erroFoto || erros.foto ? "text-danger" : "text-muted"}`}>
            {erroFoto ?? erros.foto ?? "JPEG, PNG ou WebP, até 5 MB."}
          </p>
        </div>
        <div>
          <label htmlFor="campo-observacoes" className="mb-1 block text-sm font-medium">Observações</label>
          <textarea id="campo-observacoes" name="observacoes" rows={3} maxLength={2000} className="campo" defaultValue={valor("observacoes", pet?.observacoes ?? "")} />
        </div>
      </fieldset>

      <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        <Link href={pet ? `/pets/${pet.id}` : "/pets"} className="botao-secundario">Cancelar</Link>
        <button type="submit" className="botao-primario" disabled={pending || Boolean(erroFoto)}>
          {pending ? "Salvando…" : pet ? "Salvar alterações" : "Cadastrar pet"}
        </button>
      </div>
    </form>
  );
}
