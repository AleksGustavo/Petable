"use client";

import { useActionState } from "react";
import { registrarConsulta } from "@/app/actions/pets";
import { Campo } from "@/components/campo";
import type { FormState } from "@/lib/clientes";
import { agoraLocalISO } from "@/lib/datas";

const TEXTOS = [
  { name: "motivo", rotulo: "Motivo" },
  { name: "diagnostico", rotulo: "Diagnóstico" },
  { name: "tratamento", rotulo: "Tratamento" },
  { name: "observacoes", rotulo: "Observações" },
];

export function ConsultaForm({ petId }: { petId: number }) {
  const [state, action, pending] = useActionState<FormState, FormData>(registrarConsulta.bind(null, petId), {});
  const erros = state.erros ?? {};
  const agora = agoraLocalISO();

  return (
    <form action={action} className="grid gap-4 sm:grid-cols-2" noValidate>
      {state.mensagem && (
        <p role="alert" className="rounded-lg bg-danger-soft px-4 py-3 text-sm text-danger sm:col-span-2">
          {state.mensagem}
        </p>
      )}
      <Campo rotulo="Data e hora" name="dataConsulta" type="datetime-local" required max={agora} defaultValue={state.valores?.dataConsulta ?? agora} erro={erros.dataConsulta} />
      <Campo rotulo="Veterinário" name="veterinario" required maxLength={100} defaultValue={state.valores?.veterinario} erro={erros.veterinario} />
      {TEXTOS.map((t) => (
        <div key={t.name}>
          <label htmlFor={`campo-consulta-${t.name}`} className="mb-1 block text-sm font-medium">{t.rotulo}</label>
          <textarea id={`campo-consulta-${t.name}`} name={t.name} rows={2} maxLength={2000} className="campo" defaultValue={state.valores?.[t.name]} />
          {erros[t.name] && <p className="mt-1 text-xs text-danger">{erros[t.name]}</p>}
        </div>
      ))}
      <div className="sm:col-span-2 sm:text-right">
        <button type="submit" className="botao-primario w-full sm:w-auto" disabled={pending}>
          {pending ? "Registrando…" : "Registrar consulta"}
        </button>
      </div>
    </form>
  );
}
