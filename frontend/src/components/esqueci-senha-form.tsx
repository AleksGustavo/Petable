"use client";

import Link from "next/link";
import { useActionState } from "react";
import { solicitarRedefinicao, type RecuperacaoState } from "@/app/actions/usuarios";
import { Aviso } from "@/components/aviso";
import { Campo } from "@/components/campo";

export function EsqueciSenhaForm() {
  const [state, action, pending] = useActionState<RecuperacaoState, FormData>(solicitarRedefinicao, {});
  if (state.enviado) {
    return (
      <div className="space-y-4">
        <Aviso>{state.mensagem}</Aviso>
        <p className="text-sm text-muted">O link vale por 30 minutos. Confira também a caixa de spam.</p>
        <Link href="/login" className="botao-secundario w-full">Voltar para o login</Link>
      </div>
    );
  }
  return (
    <form action={action} className="space-y-4" noValidate>
      {state.mensagem && <Aviso tipo="erro">{state.mensagem}</Aviso>}
      <Campo rotulo="E-mail da conta" name="email" type="email" required autoComplete="email" autoFocus erro={state.erros?.email} />
      <button type="submit" className="botao-primario w-full" disabled={pending}>{pending ? "Enviando…" : "Enviar instruções"}</button>
      <Link href="/login" className="block text-center text-sm text-brand hover:underline">Lembrei a senha</Link>
    </form>
  );
}
