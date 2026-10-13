"use client";

import Link from "next/link";
import { useActionState } from "react";
import { estornarLancamento } from "@/app/actions/financeiro";
import { Aviso } from "@/components/aviso";
import { BadgeTipo } from "@/components/financeiro-badges";
import { moeda } from "@/lib/agendamentos";
import type { FormState } from "@/lib/clientes";
import { rotuloCategoria, rotuloOrigem, type Lancamento } from "@/lib/financeiro";
import { formatarData } from "@/lib/format";
import { rotuloForma } from "@/lib/vendas";

function EstornoForm({ lancamento }: { lancamento: Lancamento }) {
  const [state, action, pending] = useActionState<FormState, FormData>(estornarLancamento.bind(null, lancamento.id), {});
  return (
    <form
      action={action}
      onSubmit={(e) => {
        if (!window.confirm(`Estornar o lançamento #${lancamento.id} de ${moeda(lancamento.valor)}? Um lançamento inverso será criado no seu caixa.`)) {
          e.preventDefault();
        }
      }}
      className="cartao space-y-3 p-4 sm:p-6"
    >
      <h2 className="text-sm font-semibold">Estornar lançamento</h2>
      <p className="text-sm text-muted">Lançamentos não são editados nem apagados: o estorno cria o lançamento inverso, vinculado a este.</p>
      {state.mensagem && <Aviso tipo="erro">{state.mensagem}</Aviso>}
      <div>
        <label htmlFor="campo-motivo-estorno" className="mb-1 block text-sm font-medium">
          Motivo<span className="text-danger"> *</span>
        </label>
        <textarea
          id="campo-motivo-estorno"
          name="motivo"
          rows={2}
          minLength={5}
          maxLength={500}
          required
          className="campo"
          defaultValue={state.valores?.motivo ?? ""}
          aria-invalid={state.erros?.motivo ? true : undefined}
        />
        {state.erros?.motivo && <p className="mt-1 text-xs text-danger">{state.erros.motivo}</p>}
      </div>
      <button type="submit" className="botao-perigo w-full sm:w-auto" disabled={pending}>
        {pending ? "Estornando…" : "Estornar lançamento"}
      </button>
    </form>
  );
}

export function LancamentoDetalhes({ lancamento: l }: { lancamento: Lancamento }) {
  const dados: [string, React.ReactNode][] = [
    ["Data", formatarData(l.dataMovimentacao)],
    ["Categoria", rotuloCategoria(l.categoria)],
    ["Forma de pagamento", l.formaPagamento ? rotuloForma(l.formaPagamento) : "—"],
    ["Origem", rotuloOrigem(l.origem)],
    ["Usuário", l.usuario?.nome ?? "—"],
    ["Caixa", l.caixaId ? <Link key="c" href={`/caixa/${l.caixaId}`} className="text-brand hover:underline">#{l.caixaId}</Link> : "—"],
  ];
  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center gap-3">
        <BadgeTipo tipo={l.tipo} />
        <p className={`text-2xl font-semibold tabular-nums ${l.tipo === "ENTRADA" ? "text-brand-strong" : "text-danger"}`}>
          {l.tipo === "ENTRADA" ? "+" : "−"} {moeda(l.valor)}
        </p>
      </div>
      {l.estornoDeId && (
        <Aviso>
          Este lançamento é o estorno do{" "}
          <Link href={`/financeiro/lancamentos/${l.estornoDeId}`} className="font-medium underline">lançamento #{l.estornoDeId}</Link>
          {l.motivoEstorno && `. Motivo: ${l.motivoEstorno}`}
        </Aviso>
      )}
      {l.estornadoPorId && (
        <Aviso tipo="erro">
          Estornado pelo{" "}
          <Link href={`/financeiro/lancamentos/${l.estornadoPorId}`} className="font-medium underline">lançamento #{l.estornadoPorId}</Link>.
        </Aviso>
      )}
      <dl className="cartao grid gap-4 p-4 sm:grid-cols-3 sm:p-6">
        {dados.map(([rotulo, valor]) => (
          <div key={rotulo}>
            <dt className="text-xs uppercase text-muted">{rotulo}</dt>
            <dd className="font-medium">{valor}</dd>
          </div>
        ))}
        {l.observacoes && (
          <div className="sm:col-span-3">
            <dt className="text-xs uppercase text-muted">Observações</dt>
            <dd className="whitespace-pre-line text-sm">{l.observacoes}</dd>
          </div>
        )}
      </dl>
      {l.estornavel ? (
        <EstornoForm lancamento={l} />
      ) : (
        !l.estornadoPorId &&
        !l.estornoDeId && (
          <p className="text-sm text-muted">Lançamento automático: é corrigido pelo módulo de origem (ex.: cancelamento da venda).</p>
        )
      )}
    </div>
  );
}
