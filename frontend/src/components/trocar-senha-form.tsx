"use client";

import { useActionState, useState } from "react";
import { trocarMinhaSenha } from "@/app/actions/usuarios";
import { Aviso } from "@/components/aviso";
import { Campo } from "@/components/campo";
import { RequisitosSenha } from "@/components/requisitos-senha";
import type { FormState } from "@/lib/clientes";

export function TrocarSenhaForm({ username, email }: { username: string; email: string }) {
  const [state, action, pending] = useActionState<FormState, FormData>(trocarMinhaSenha, {});
  const [nova, setNova] = useState("");
  const erros = state.erros ?? {};
  return (
    <form action={action} className="grid gap-4 sm:grid-cols-2" noValidate>
      {state.mensagem && <div className="sm:col-span-2"><Aviso tipo="erro">{state.mensagem}</Aviso></div>}
      {/* Para a política (senha ≠ username/e-mail) e para gerenciadores de senha. */}
      <input type="hidden" name="username" value={username} />
      <input type="hidden" name="email" value={email} />
      <Campo className="sm:col-span-2" rotulo="Senha atual" name="senhaAtual" type="password" required autoComplete="current-password" erro={erros.senhaAtual} />
      <div>
        <Campo rotulo="Nova senha" name="novaSenha" type="password" required autoComplete="new-password" maxLength={72} value={nova} onChange={(e) => setNova(e.target.value)} erro={erros.novaSenha} aria-describedby="requisitos-troca" />
        <RequisitosSenha senha={nova} id="requisitos-troca" />
      </div>
      <Campo rotulo="Confirme a nova senha" name="confirmacaoNovaSenha" type="password" required autoComplete="new-password" maxLength={72} erro={erros.confirmacaoNovaSenha} />
      <p className="text-xs text-muted sm:col-span-2">Depois da troca, todas as suas sessões são encerradas e você entra de novo com a nova senha.</p>
      <div className="sm:col-span-2 sm:text-right">
        <button type="submit" className="botao-primario w-full sm:w-auto" disabled={pending}>{pending ? "Alterando…" : "Alterar senha"}</button>
      </div>
    </form>
  );
}
