"use client";

import Link from "next/link";
import { useActionState, useState } from "react";
import { criarUsuario } from "@/app/actions/usuarios";
import { Aviso } from "@/components/aviso";
import { Campo } from "@/components/campo";
import { RequisitosSenha } from "@/components/requisitos-senha";
import type { FormState } from "@/lib/clientes";
import { PAPEIS } from "@/lib/usuarios";

export function NovoUsuarioForm() {
  const [state, action, pending] = useActionState<FormState, FormData>(criarUsuario, {});
  const [senha, setSenha] = useState("");
  const erros = state.erros ?? {};
  return (
    <form action={action} className="space-y-6" noValidate>
      {state.mensagem && <Aviso tipo="erro">{state.mensagem}</Aviso>}
      <fieldset className="cartao grid gap-4 p-4 sm:grid-cols-2 sm:p-6">
        <legend className="px-1 text-sm font-semibold text-muted">Conta</legend>
        <Campo rotulo="Nome de usuário" name="username" required autoComplete="off" maxLength={50} defaultValue={state.valores?.username} erro={erros.username} ajuda="Letras, números, ponto, hífen ou sublinhado." />
        <Campo rotulo="E-mail" name="email" type="email" required autoComplete="off" maxLength={120} defaultValue={state.valores?.email} erro={erros.email} />
        <div>
          <Campo rotulo="Senha inicial" name="senha" type="password" required autoComplete="new-password" maxLength={72} value={senha} onChange={(e) => setSenha(e.target.value)} erro={erros.senha} aria-describedby="requisitos-nova-conta" />
          <RequisitosSenha senha={senha} id="requisitos-nova-conta" />
        </div>
        <Campo rotulo="Confirme a senha" name="confirmacaoSenha" type="password" required autoComplete="new-password" maxLength={72} erro={erros.confirmacaoSenha} />
      </fieldset>
      <fieldset className="cartao space-y-3 p-4 sm:p-6">
        <legend className="px-1 text-sm font-semibold text-muted">Papel</legend>
        {PAPEIS.map((p) => (
          <label key={p.valor} className="flex cursor-pointer items-start gap-3 rounded-lg border border-border p-3 has-[:checked]:border-brand has-[:checked]:bg-brand-soft/40">
            <input type="radio" name="papel" value={p.valor} defaultChecked={(state.valores?.papel ?? "VENDAS") === p.valor} className="mt-1 accent-[var(--brand)]" />
            <span>
              <span className="block text-sm font-medium">{p.rotulo}</span>
              <span className="block text-xs text-muted">{p.descricao}</span>
            </span>
          </label>
        ))}
        {erros.papel && <p className="text-xs text-danger">{erros.papel}</p>}
      </fieldset>
      <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        <Link href="/usuarios" className="botao-secundario">Cancelar</Link>
        <button type="submit" className="botao-primario" disabled={pending}>{pending ? "Criando…" : "Criar usuário"}</button>
      </div>
    </form>
  );
}
