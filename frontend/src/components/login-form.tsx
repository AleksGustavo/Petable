"use client";

import { useActionState } from "react";
import { entrar, type LoginState } from "@/app/actions/auth";
import { Campo } from "@/components/campo";

export function LoginForm({ next }: { next?: string }) {
  const [state, action, pending] = useActionState<LoginState, FormData>(entrar, {});

  return (
    <form action={action} className="space-y-4" noValidate>
      {next && <input type="hidden" name="next" value={next} />}
      <Campo rotulo="Usuário ou e-mail" name="login" autoComplete="username" required defaultValue={state.login} autoFocus />
      <Campo rotulo="Senha" name="senha" type="password" autoComplete="current-password" required />
      {state.erro && (
        <p role="alert" className="rounded-lg bg-danger-soft px-3 py-2 text-sm text-danger">
          {state.erro}
        </p>
      )}
      <button type="submit" className="botao-primario w-full" disabled={pending}>
        {pending ? "Entrando…" : "Entrar"}
      </button>
    </form>
  );
}
