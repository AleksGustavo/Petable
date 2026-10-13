"use client";

import { useFormStatus } from "react-dom";

function Botao({ rotulo, className }: { rotulo: string; className: string }) {
  const { pending } = useFormStatus();
  return (
    <button type="submit" className={className} disabled={pending}>
      {pending ? "Aguarde…" : rotulo}
    </button>
  );
}

/** Form de um botão só que pede confirmação antes de disparar a Server Action. */
export function BotaoConfirmar({
  action,
  rotulo,
  confirmacao,
  className = "botao-perigo",
}: {
  action: () => Promise<void>;
  rotulo: string;
  confirmacao: string;
  className?: string;
}) {
  return (
    <form
      action={action}
      onSubmit={(e) => {
        if (!window.confirm(confirmacao)) e.preventDefault();
      }}
    >
      <Botao rotulo={rotulo} className={className} />
    </form>
  );
}
