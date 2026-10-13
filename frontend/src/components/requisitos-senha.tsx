"use client";

import { REQUISITOS_SENHA } from "@/lib/usuarios";

/** Lista viva dos requisitos: o estado é dito em texto ("atendido"), não só pela cor. */
export function RequisitosSenha({ senha, id }: { senha: string; id: string }) {
  return (
    <ul id={id} className="mt-2 grid gap-1 text-xs sm:grid-cols-2" aria-label="Requisitos da senha">
      {REQUISITOS_SENHA.map((r) => {
        const ok = r.ok(senha);
        return (
          <li key={r.rotulo} className={ok ? "text-brand-strong" : "text-muted"}>
            <span aria-hidden="true">{ok ? "✓" : "○"}</span> {r.rotulo}
            <span className="sr-only">{ok ? " (atendido)" : " (pendente)"}</span>
          </li>
        );
      })}
      <li className="text-muted sm:col-span-2">Diferente do nome de usuário e do e-mail</li>
    </ul>
  );
}
