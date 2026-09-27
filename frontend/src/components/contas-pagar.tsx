"use client";

import Link from "next/link";
import { useActionState } from "react";
import { pagarConta } from "@/app/actions/financeiro";
import { Aviso } from "@/components/aviso";
import { BadgeSituacao } from "@/components/financeiro-badges";
import { moeda } from "@/lib/agendamentos";
import type { FormState } from "@/lib/clientes";
import { formatarDataHora } from "@/lib/datas";
import { MENSAGEM_SEM_CAIXA, SITUACOES_CONTA, type ContaPagar } from "@/lib/financeiro";
import { formatarData } from "@/lib/format";
import { FORMAS_PAGAMENTO, rotuloForma } from "@/lib/vendas";

export function ContasFiltros({ situacao }: { situacao: string }) {
  return (
    <nav aria-label="Situação das contas" className="flex gap-1 overflow-x-auto rounded-lg border border-border bg-surface p-1">
      {[{ valor: "", rotulo: "Todas" }, ...SITUACOES_CONTA].map((s) => (
        <Link
          key={s.valor}
          href={s.valor ? `/financeiro/contas-a-pagar?situacao=${s.valor}` : "/financeiro/contas-a-pagar"}
          aria-current={situacao === s.valor ? "page" : undefined}
          className={`flex-1 whitespace-nowrap rounded-md px-3 py-2 text-center text-sm font-medium ${
            situacao === s.valor ? "bg-brand text-white" : "text-muted hover:bg-brand-soft"
          }`}
        >
          {s.rotulo}
        </Link>
      ))}
    </nav>
  );
}

export function ContasLista({ contas, filtrado = false }: { contas: ContaPagar[]; filtrado?: boolean }) {
  if (contas.length === 0) {
    return (
      <p className="cartao p-10 text-center text-sm text-muted">
        {filtrado ? "Nenhuma conta nesta situação." : "Nenhuma conta a pagar. Elas são criadas ao receber uma compra."}
      </p>
    );
  }
  return (
    <>
      <ul className="space-y-3 md:hidden">
        {contas.map((c) => (
          <li key={c.id}>
            <Link href={`/financeiro/contas-a-pagar/${c.id}`} className="cartao block space-y-1 p-4 transition hover:border-brand">
              <div className="flex items-start justify-between gap-2">
                <p className="font-medium">{c.descricao}</p>
                <BadgeSituacao situacao={c.situacao} />
              </div>
              <p className="text-sm text-muted">
                {c.fornecedor?.nome ?? "—"} · vence {formatarData(c.dataVencimento)}
              </p>
              <p className="font-semibold tabular-nums">{moeda(c.valorOriginal)}</p>
            </Link>
          </li>
        ))}
      </ul>
      <div className="cartao hidden overflow-x-auto md:block">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-border text-xs uppercase tracking-wide text-muted">
            <tr>
              <th scope="col" className="px-4 py-3 font-medium">Vencimento</th>
              <th scope="col" className="px-4 py-3 font-medium">Descrição</th>
              <th scope="col" className="px-4 py-3 font-medium">Fornecedor</th>
              <th scope="col" className="px-4 py-3 text-right font-medium">Valor</th>
              <th scope="col" className="px-4 py-3 font-medium">Situação</th>
              <th scope="col" className="px-4 py-3"><span className="sr-only">Ações</span></th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {contas.map((c) => (
              <tr key={c.id} className="hover:bg-brand-soft/40">
                <td className="whitespace-nowrap px-4 py-3 tabular-nums">{formatarData(c.dataVencimento)}</td>
                <td className="px-4 py-3 font-medium">{c.descricao}</td>
                <td className="px-4 py-3">{c.fornecedor?.nome ?? "—"}</td>
                <td className="px-4 py-3 text-right tabular-nums">{moeda(c.valorOriginal)}</td>
                <td className="px-4 py-3"><BadgeSituacao situacao={c.situacao} /></td>
                <td className="px-4 py-3 text-right">
                  <Link href={`/financeiro/contas-a-pagar/${c.id}`} className="font-medium text-brand hover:underline">Abrir</Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

function PagarForm({ conta, caixaAberto }: { conta: ContaPagar; caixaAberto: boolean }) {
  const [state, action, pending] = useActionState<FormState, FormData>(pagarConta.bind(null, conta.id), {});
  return (
    <form
      action={action}
      onSubmit={(e) => {
        const forma = String(new FormData(e.currentTarget).get("formaPagamento") ?? "");
        if (!forma) return;
        const rotulo = FORMAS_PAGAMENTO.find((f) => f.valor === forma)?.rotulo ?? forma;
        if (!window.confirm(`Pagar ${moeda(conta.valorOriginal)} (valor integral) em ${rotulo}? A saída entra no seu caixa aberto.`)) {
          e.preventDefault();
        }
      }}
      className="cartao space-y-3 p-4 sm:p-6"
    >
      <h2 className="text-sm font-semibold">Registrar pagamento</h2>
      {!caixaAberto && (
        <Aviso tipo="erro">
          {MENSAGEM_SEM_CAIXA} <Link href="/caixa" className="font-medium underline">Abrir caixa</Link>
        </Aviso>
      )}
      {state.mensagem && <Aviso tipo="erro">{state.mensagem}</Aviso>}
      <div>
        <label htmlFor="campo-forma-conta" className="mb-1 block text-sm font-medium">
          Forma de pagamento<span className="text-danger"> *</span>
        </label>
        <select key={state.valores?.formaPagamento ?? ""} id="campo-forma-conta" name="formaPagamento" className="campo" defaultValue={state.valores?.formaPagamento ?? ""}>
          <option value="">Selecione…</option>
          {FORMAS_PAGAMENTO.map((f) => (
            <option key={f.valor} value={f.valor}>{f.rotulo}</option>
          ))}
        </select>
        {state.erros?.formaPagamento && <p className="mt-1 text-xs text-danger">{state.erros.formaPagamento}</p>}
      </div>
      <p className="text-sm text-muted">Pagamento integral de {moeda(conta.valorOriginal)}. Não há pagamento parcial.</p>
      <button type="submit" className="botao-primario w-full sm:w-auto" disabled={pending || !caixaAberto}>
        {pending ? "Pagando…" : "Pagar conta"}
      </button>
    </form>
  );
}

export function ContaDetalhes({ conta, caixaAberto }: { conta: ContaPagar; caixaAberto: boolean }) {
  const dados: [string, React.ReactNode][] = [
    ["Fornecedor", conta.fornecedor?.nome ?? "—"],
    ["Compra", conta.compraId ? <Link key="c" href={`/compras/${conta.compraId}`} className="text-brand hover:underline">#{conta.compraId}</Link> : "—"],
    ["Vencimento", formatarData(conta.dataVencimento)],
    ["Valor", moeda(conta.valorOriginal)],
    ["Saldo", moeda(conta.saldo)],
  ];
  if (conta.status === "PAGA") {
    dados.push(
      ["Paga em", formatarDataHora(conta.pagaEm)],
      ["Forma", conta.formaPagamento ? rotuloForma(conta.formaPagamento) : "—"],
      ["Por", conta.usuarioPagamento?.nome ?? "—"],
      ["Lançamento", conta.lancamentoId ? <Link key="l" href={`/financeiro/lancamentos/${conta.lancamentoId}`} className="text-brand hover:underline">#{conta.lancamentoId}</Link> : "—"],
    );
  }
  return (
    <div className="space-y-6">
      <BadgeSituacao situacao={conta.situacao} />
      <dl className="cartao grid gap-4 p-4 sm:grid-cols-3 sm:p-6">
        {dados.map(([rotulo, valor]) => (
          <div key={rotulo}>
            <dt className="text-xs uppercase text-muted">{rotulo}</dt>
            <dd className="font-medium tabular-nums">{valor}</dd>
          </div>
        ))}
      </dl>
      {conta.status === "ABERTA" && <PagarForm conta={conta} caixaAberto={caixaAberto} />}
    </div>
  );
}
