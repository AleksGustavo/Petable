"use client";

import { useActionState } from "react";
import { Aviso } from "@/components/aviso";
import { Campo } from "@/components/campo";
import type { FormState } from "@/lib/clientes";

/** Username + e-mail (usado pelo ADMIN e pelo próprio usuário). */
export function ContaForm({
  action,
  username,
  email,
}: {
  action: (prev: FormState, formData: FormData) => Promise<FormState>;
  username: string;
  email: string;
}) {
  const [state, formAction, pending] = useActionState<FormState, FormData>(action, {});
  const erros = state.erros ?? {};
  return (
    <form action={formAction} className="grid gap-4 sm:grid-cols-2" noValidate>
      {state.mensagem && <div className="sm:col-span-2"><Aviso tipo="erro">{state.mensagem}</Aviso></div>}
      <Campo rotulo="Nome de usuário" name="username" required autoComplete="username" maxLength={50} defaultValue={state.valores?.username ?? username} erro={erros.username} />
      <Campo rotulo="E-mail" name="email" type="email" required autoComplete="email" maxLength={120} defaultValue={state.valores?.email ?? email} erro={erros.email} />
      <div className="sm:col-span-2 sm:text-right">
        <button type="submit" className="botao-primario w-full sm:w-auto" disabled={pending}>
          {pending ? "Salvando…" : "Salvar conta"}
        </button>
      </div>
    </form>
  );
}
