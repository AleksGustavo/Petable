"use client";

import { useActionState, useState } from "react";
import { registrarVacina } from "@/app/actions/pets";
import { Campo } from "@/components/campo";
import type { FormState } from "@/lib/clientes";
import { hojeISO, somarMeses } from "@/lib/datas";
import type { VacinaCatalogo } from "@/lib/pets";

/** Vacina escolhida do catálogo da espécie do pet (nunca texto livre). */
export function VacinaForm({ petId, vacinas }: { petId: number; vacinas: VacinaCatalogo[] }) {
  const [state, action, pending] = useActionState<FormState, FormData>(registrarVacina.bind(null, petId), {});
  const erros = state.erros ?? {};
  const hoje = hojeISO();
  const [vacinaId, setVacinaId] = useState(state.valores?.vacinaId ?? "");
  const [aplicacao, setAplicacao] = useState(state.valores?.dataAplicacao ?? hoje);
  const [proxima, setProxima] = useState(state.valores?.dataProxima ?? "");

  /** Sugere a próxima dose pela validade padrão da vacina; o usuário pode mudar. */
  function sugerir(id: string, dataAplicacao: string) {
    const v = vacinas.find((x) => String(x.id) === id);
    if (v && dataAplicacao) setProxima(somarMeses(dataAplicacao, v.validadePadraoMeses));
  }

  if (vacinas.length === 0) {
    return <p className="text-sm text-muted">Não há vacinas ativas no catálogo para esta espécie.</p>;
  }

  return (
    <form action={action} className="grid gap-4 sm:grid-cols-2" noValidate>
      {state.mensagem && (
        <p role="alert" className="rounded-lg bg-danger-soft px-4 py-3 text-sm text-danger sm:col-span-2">
          {state.mensagem}
        </p>
      )}
      <div className="sm:col-span-2">
        <label htmlFor="campo-vacina" className="mb-1 block text-sm font-medium">
          Vacina<span className="text-danger"> *</span>
        </label>
        <select
          id="campo-vacina"
          name="vacinaId"
          className="campo"
          value={vacinaId}
          onChange={(e) => {
            setVacinaId(e.target.value);
            sugerir(e.target.value, aplicacao);
          }}
          aria-invalid={erros.vacinaId ? true : undefined}
        >
          <option value="">Selecione…</option>
          {vacinas.map((v) => (
            <option key={v.id} value={v.id}>
              {v.nome}
              {v.doencaProtecao ? ` — ${v.doencaProtecao}` : ""}
            </option>
          ))}
        </select>
        {erros.vacinaId && <p className="mt-1 text-xs text-danger">{erros.vacinaId}</p>}
      </div>
      <Campo
        rotulo="Data de aplicação"
        name="dataAplicacao"
        type="date"
        required
        max={hoje}
        value={aplicacao}
        onChange={(e) => {
          setAplicacao(e.target.value);
          sugerir(vacinaId, e.target.value);
        }}
        erro={erros.dataAplicacao}
      />
      <Campo
        rotulo="Próxima dose"
        name="dataProxima"
        type="date"
        min={aplicacao}
        value={proxima}
        onChange={(e) => setProxima(e.target.value)}
        erro={erros.dataProxima}
        ajuda="Sugerida pela validade da vacina. Deixe vazio se não houver."
      />
      <Campo rotulo="Veterinário" name="veterinario" maxLength={100} defaultValue={state.valores?.veterinario} erro={erros.veterinario} />
      <Campo rotulo="Lote" name="lote" maxLength={50} defaultValue={state.valores?.lote} erro={erros.lote} />
      <div className="sm:col-span-2">
        <label htmlFor="campo-obs-vacina" className="mb-1 block text-sm font-medium">Observações</label>
        <textarea id="campo-obs-vacina" name="observacoes" rows={2} maxLength={2000} className="campo" defaultValue={state.valores?.observacoes} />
      </div>
      <div className="sm:col-span-2 sm:text-right">
        <button type="submit" className="botao-primario w-full sm:w-auto" disabled={pending}>
          {pending ? "Registrando…" : "Registrar vacina"}
        </button>
      </div>
    </form>
  );
}
