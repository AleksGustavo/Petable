"use client";

import Link from "next/link";
import { useActionState } from "react";
import { cancelarVenda } from "@/app/actions/vendas";
import { Aviso } from "@/components/aviso";
import { BadgeStatusVenda } from "@/components/vendas-lista";
import { moeda } from "@/lib/agendamentos";
import type { FormState } from "@/lib/clientes";
import { formatarDataHora } from "@/lib/datas";
import { rotuloForma, type Venda } from "@/lib/vendas";

function CancelarVendaForm({ venda }: { venda: Venda }) {
  const [state, action, pending] = useActionState<FormState, FormData>(cancelarVenda.bind(null, venda.id), {});
  return (
    <form
      action={action}
      onSubmit={(e) => {
        const itens = venda.itens?.reduce((s, i) => s + i.quantidade, 0) ?? 0;
        if (!window.confirm(`Cancelar a venda #${venda.id} de ${moeda(venda.valorTotal)}? ${itens} unidade(s) voltarão ao estoque. Esta ação não pode ser desfeita.`)) {
          e.preventDefault();
        }
      }}
      className="cartao space-y-3 p-4 sm:p-6"
    >
      <h2 className="text-sm font-semibold">Cancelar venda</h2>
      <p className="text-sm text-muted">O estoque de todos os itens será estornado. O cancelamento fica registrado com o seu usuário.</p>
      {state.mensagem && <Aviso tipo="erro">{state.mensagem}</Aviso>}
      <div>
        <label htmlFor="campo-motivo-venda" className="mb-1 block text-sm font-medium">
          Motivo<span className="text-danger"> *</span>
        </label>
        <textarea
          id="campo-motivo-venda"
          name="motivo"
          rows={2}
          required
          minLength={5}
          maxLength={500}
          className="campo"
          defaultValue={state.valores?.motivo ?? ""}
          aria-invalid={state.erros?.motivo ? true : undefined}
        />
        {state.erros?.motivo && <p className="mt-1 text-xs text-danger">{state.erros.motivo}</p>}
      </div>
      <button type="submit" className="botao-perigo w-full sm:w-auto" disabled={pending}>
        {pending ? "Cancelando…" : "Cancelar venda"}
      </button>
    </form>
  );
}

export function VendaDetalhes({ venda, podeCancelar }: { venda: Venda; podeCancelar: boolean }) {
  const dados: [string, string][] = [
    ["Data", formatarDataHora(venda.dataVenda)],
    ["Cliente", venda.cliente?.nome ?? "Não identificado"],
    ["Pagamento", rotuloForma(venda.formaPagamento)],
    ["Operador", venda.operador?.nome ?? "—"],
    ["Funcionário", venda.funcionario?.nome ?? "—"],
  ];
  const itens = venda.itens ?? [];
  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center gap-3">
        <BadgeStatusVenda status={venda.status} />
        <Link href={`/vendas/${venda.id}/comprovante`} target="_blank" className="text-sm font-medium text-brand hover:underline">
          Comprovante ↗
        </Link>
      </div>

      {venda.status === "CANCELADA" && (
        <Aviso tipo="erro">
          Cancelada em {formatarDataHora(venda.canceladaEm)} por {venda.canceladaPor?.nome ?? "—"}. Motivo: {venda.motivoCancelamento}
        </Aviso>
      )}

      <dl className="cartao grid gap-4 p-4 sm:grid-cols-3 sm:p-6">
        {dados.map(([rotulo, valor]) => (
          <div key={rotulo}>
            <dt className="text-xs uppercase text-muted">{rotulo}</dt>
            <dd className="font-medium">{valor}</dd>
          </div>
        ))}
        {venda.observacoes && (
          <div className="sm:col-span-3">
            <dt className="text-xs uppercase text-muted">Observações</dt>
            <dd className="whitespace-pre-line text-sm">{venda.observacoes}</dd>
          </div>
        )}
      </dl>

      <section className="cartao overflow-hidden" aria-labelledby="sec-itens-venda">
        <h2 id="sec-itens-venda" className="px-4 pt-4 text-sm font-semibold text-muted sm:px-6">Itens ({itens.length})</h2>
        <ul className="divide-y divide-border">
          {itens.map((i) => (
            <li key={i.id} className="flex items-baseline justify-between gap-3 px-4 py-3 text-sm sm:px-6">
              <span className="min-w-0">
                <Link href={`/produtos/${i.produto.id}`} className="font-medium hover:text-brand">{i.produto.nome}</Link>
                <span className="block text-muted tabular-nums">
                  {i.quantidade} × {moeda(i.precoUnitario)}
                </span>
              </span>
              <span className="font-medium tabular-nums">{moeda(i.subtotal)}</span>
            </li>
          ))}
        </ul>
        <dl className="space-y-1 border-t border-border px-4 py-4 text-sm sm:px-6">
          <div className="flex justify-between"><dt className="text-muted">Subtotal</dt><dd className="tabular-nums">{moeda(venda.subtotal)}</dd></div>
          <div className="flex justify-between"><dt className="text-muted">Desconto</dt><dd className="tabular-nums">− {moeda(venda.desconto)}</dd></div>
          <div className="flex items-baseline justify-between">
            <dt className="font-medium">Total</dt>
            <dd className="text-xl font-semibold tabular-nums" data-testid="total-venda">{moeda(venda.valorTotal)}</dd>
          </div>
          {venda.valorRecebido !== null && (
            <>
              <div className="flex justify-between"><dt className="text-muted">Recebido</dt><dd className="tabular-nums">{moeda(venda.valorRecebido)}</dd></div>
              <div className="flex justify-between"><dt className="text-muted">Troco</dt><dd className="tabular-nums">{moeda(venda.troco)}</dd></div>
            </>
          )}
        </dl>
      </section>

      {venda.status === "CONCLUIDA" && podeCancelar && <CancelarVendaForm venda={venda} />}
    </div>
  );
}
