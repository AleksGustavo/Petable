"use client";

import Link from "next/link";
import { useActionState, useState } from "react";
import { redefinirSenha, type RecuperacaoState } from "@/app/actions/usuarios";
import { Aviso } from "@/components/aviso";
import { Campo } from "@/components/campo";
import { RequisitosSenha } from "@/components/requisitos-senha";

/** O token vem da URL e vai num campo escondido: nunca é exibido na tela. */
export function RedefinirSenhaForm({ token }: { token: string | null }) {
  const [state, action, pending] = useActionState<RecuperacaoState, FormData>(redefinirSenha, {});
  const [nova, setNova] = useState("");

  if (!token) {
    return (
      <div className="space-y-4">
        <Aviso tipo="erro">O link de redefinição é inválido ou está incompleto.</Aviso>
        <Link href="/esqueci-senha" className="botao-secundario w-full">Pedir um novo link</Link>
      </div>
    );
  }
  if (state.sucesso) {
    return (
      <div className="space-y-4">
        <Aviso>Senha redefinida. Entre com a nova senha.</Aviso>
        <Link href="/login?aviso=senha-redefinida" className="botao-primario w-full">Ir para o login</Link>
      </div>
    );
  }
  const erros = state.erros ?? {};
  return (
    <form action={action} className="space-y-4" noValidate>
      {state.mensagem && (
        <div className="space-y-2">
          <Aviso tipo="erro">{state.mensagem}</Aviso>
          {erros.token && <Link href="/esqueci-senha" className="text-sm text-brand hover:underline">Pedir um novo link</Link>}
        </div>
      )}
      <input type="hidden" name="token" value={token} />
      <div>
        <Campo rotulo="Nova senha" name="novaSenha" type="password" required autoComplete="new-password" maxLength={72} value={nova} onChange={(e) => setNova(e.target.value)} erro={erros.novaSenha} aria-describedby="requisitos-redefinicao" autoFocus />
        <RequisitosSenha senha={nova} id="requisitos-redefinicao" />
      </div>
      <Campo rotulo="Confirme a nova senha" name="confirmacaoNovaSenha" type="password" required autoComplete="new-password" maxLength={72} erro={erros.confirmacaoNovaSenha} />
      <button type="submit" className="botao-primario w-full" disabled={pending}>{pending ? "Salvando…" : "Redefinir senha"}</button>
    </form>
  );
}
