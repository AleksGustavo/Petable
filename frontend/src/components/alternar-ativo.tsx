"use client";

import { useActionState } from "react";
import { Aviso } from "@/components/aviso";
import type { FormState } from "@/lib/clientes";

/**
 * Botão de desativar/reativar com confirmação. A Server Action já vem com o id e
 * o sentido ligados; um erro da API (ex.: 409) aparece logo acima do botão.
 */
export function AlternarAtivo({
  action,
  ativo,
  rotulo,
  confirmacao,
}: {
  action: () => Promise<FormState>;
  ativo: boolean;
  rotulo: string;
  confirmacao: string;
}) {
  const [state, acao, pendente] = useActionState<FormState, FormData>(() => action(), {});
  return (
    <div className="space-y-2">
      {state.mensagem && <Aviso tipo="erro">{state.mensagem}</Aviso>}
      <form
        action={acao}
        onSubmit={(e) => {
          if (!window.confirm(confirmacao)) e.preventDefault();
        }}
      >
        <button type="submit" className={`${ativo ? "botao-perigo" : "botao-primario"} w-full sm:w-auto`} disabled={pendente}>
          {pendente ? "Aguarde…" : rotulo}
        </button>
      </form>
    </div>
  );
}
