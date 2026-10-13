"use client";

import { useActionState } from "react";
import { anonimizarCliente, type AnonimizacaoState } from "@/app/actions/clientes";

/** LGPD (só ADMIN): exportar os dados do titular e anonimizar sem apagar o histórico. */
export function ClientePrivacidade({ clienteId }: { clienteId: number }) {
  const [estado, acao, pendente] = useActionState<AnonimizacaoState, FormData>(
    anonimizarCliente.bind(null, clienteId),
    {},
  );
  return (
    <section className="cartao space-y-4 p-4" aria-labelledby="lgpd-titulo">
      <div>
        <h2 id="lgpd-titulo" className="font-semibold">Privacidade (LGPD)</h2>
        <p className="text-sm text-muted">Pedidos do titular: acesso aos dados e eliminação dos dados pessoais.</p>
      </div>
      <a href={`/api/clientes/${clienteId}/dados-pessoais`} className="botao-secundario inline-block" download>
        Exportar dados (JSON)
      </a>
      <details className="rounded-lg border border-border p-3">
        <summary className="cursor-pointer text-sm font-medium text-danger">Anonimizar cliente</summary>
        <form action={acao} className="mt-3 space-y-3">
          <p className="text-sm">
            Remove nome, CPF, telefone, e-mail, endereço e observações. <strong>Não pode ser desfeito.</strong> Vendas e
            atendimentos continuam no histórico, sem identificar a pessoa.
          </p>
          <div>
            <label htmlFor="lgpd-motivo" className="mb-1 block text-sm font-medium">Motivo</label>
            <input id="lgpd-motivo" name="motivo" className="campo" required minLength={5} maxLength={500} />
          </div>
          <div>
            <label htmlFor="lgpd-confirmacao" className="mb-1 block text-sm font-medium">Digite ANONIMIZAR para confirmar</label>
            <input id="lgpd-confirmacao" name="confirmacao" className="campo" required autoComplete="off" />
          </div>
          {estado.erro && <p role="alert" className="text-sm text-danger">{estado.erro}</p>}
          <button type="submit" className="botao-perigo" disabled={pendente}>
            {pendente ? "Anonimizando…" : "Anonimizar definitivamente"}
          </button>
        </form>
      </details>
    </section>
  );
}
