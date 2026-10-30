"use client";

import { useActionState } from "react";
import { desvincularVacina, vincularVacina } from "@/app/actions/estoque";
import { Aviso } from "@/components/aviso";
import { BotaoConfirmar } from "@/components/botao-confirmar";
import type { FormState } from "@/lib/clientes";
import type { VacinaEstoque } from "@/lib/lotes";

type Props = {
  produtoId: number;
  /** Vacinas já ligadas a este produto. */
  vinculadas: VacinaEstoque[];
  /** Vacinas ativas do catálogo (só o ADMIN as recebe); o que não está ligado a este produto pode ser escolhido. */
  catalogo: VacinaEstoque[];
  podeGerenciar: boolean;
};

/** Vacina ↔ produto: com o vínculo, aplicar a vacina tira uma dose do lote que vence primeiro e grava o lote na carteira. */
export function ProdutoVacinas({ produtoId, vinculadas, catalogo, podeGerenciar }: Props) {
  const [state, action, pending] = useActionState<FormState, FormData>(vincularVacina.bind(null, produtoId), {});
  const disponiveis = catalogo.filter((v) => v.produto?.id !== produtoId);
  return (
    <section className="cartao space-y-4 p-4 sm:p-6" aria-labelledby="sec-vacinas">
      <div>
        <h2 id="sec-vacinas" className="text-base font-semibold">Vacina do catálogo</h2>
        <p className="mt-1 text-sm text-muted">
          Se este produto é uma vacina, ligue-o à vacina do catálogo. Ao aplicar a vacina (carteira do pet ou conclusão da vacinação), o sistema tira uma dose do
          lote que vence primeiro e grava esse lote na carteira. Sem lote disponível, a aplicação não é registrada.
        </p>
      </div>
      {vinculadas.length === 0 ? (
        <p className="text-sm text-muted">Nenhuma vacina ligada a este produto.</p>
      ) : (
        <ul className="divide-y divide-border text-sm" aria-label="Vacinas ligadas ao produto">
          {vinculadas.map((v) => (
            <li key={v.id} className="flex flex-wrap items-center justify-between gap-2 py-2">
              <span>
                <strong>{v.nome}</strong> <span className="text-muted">· {v.especie}</span>
              </span>
              {podeGerenciar && (
                <BotaoConfirmar
                  action={async () => {
                    await desvincularVacina(produtoId, v.id);
                  }}
                  rotulo="Desvincular"
                  className="botao-secundario py-1 text-xs"
                  confirmacao={`Desvincular ${v.nome}? As próximas aplicações voltam a pedir o lote digitado e não baixam estoque.`}
                />
              )}
            </li>
          ))}
        </ul>
      )}
      {podeGerenciar && disponiveis.length > 0 && (
        <form action={action} className="grid gap-3 sm:grid-cols-[1fr_auto] sm:items-end" noValidate>
          {state.mensagem && (
            <div className="sm:col-span-2">
              <Aviso tipo="erro">{state.mensagem}</Aviso>
            </div>
          )}
          <div>
            <label htmlFor="campo-vincular-vacina" className="mb-1 block text-sm font-medium">Vincular vacina</label>
            {/* key: remonta com o valor devolvido pela action (o reset do React 19 não reaplica defaultValue em select). */}
            <select
              key={state.valores?.vacinaId ?? ""}
              id="campo-vincular-vacina"
              name="vacinaId"
              className="campo"
              defaultValue={state.valores?.vacinaId ?? ""}
              aria-invalid={state.erros?.vacinaId ? true : undefined}>
              <option value="">Selecione…</option>
              {disponiveis.map((v) => (
                <option key={v.id} value={v.id}>
                  {v.nome} ({v.especie}){v.produto ? ` · hoje: ${v.produto.nome}` : ""}
                </option>
              ))}
            </select>
            {state.erros?.vacinaId && <p className="mt-1 text-xs text-danger">{state.erros.vacinaId}</p>}
          </div>
          <button type="submit" className="botao-secundario" disabled={pending}>
            {pending ? "Vinculando…" : "Vincular"}
          </button>
        </form>
      )}
    </section>
  );
}
