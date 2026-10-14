"use client";

import Link from "next/link";
import { useActionState, useState } from "react";
import { cancelarCompra, receberCompra } from "@/app/actions/estoque";
import { Aviso } from "@/components/aviso";
import { Campo } from "@/components/campo";
import { GradeLotes } from "@/components/lotes-grade";
import { BadgeStatusCompra } from "@/components/estoque-badges";
import { moeda } from "@/lib/agendamentos";
import type { FormState } from "@/lib/clientes";
import type { Compra, ItemCompra } from "@/lib/estoque";
import { formatarData } from "@/lib/format";
import { linhaVazia, validarLinhas, type LinhaLote } from "@/lib/lotes";

const somarDias = (iso: string, dias: number) => {
  const d = new Date(`${iso}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + dias);
  return d.toISOString().slice(0, 10);
};

/** Lotes recebidos de um item (compra já recebida), ou o aviso de que o recebimento pedirá os lotes. */
function LotesRecebidos({ item }: { item: ItemCompra }) {
  if ((item.lotes?.length ?? 0) > 0) {
    return (
      <p className="text-xs text-muted">
        Lote(s): {item.lotes!.map((l) => `${l.codigoLote}${l.validade ? ` (val. ${formatarData(l.validade)})` : ""} × ${l.quantidade}`).join(" · ")}
      </p>
    );
  }
  return item.controlaLote ? <p className="text-xs text-muted">Controla lote: o recebimento pedirá código e validade.</p> : null;
}

function Itens({ itens, total }: { itens: ItemCompra[]; total: number }) {
  return (
    <section className="cartao overflow-hidden" aria-labelledby="sec-itens">
      <h2 id="sec-itens" className="px-4 pt-4 text-sm font-semibold text-muted sm:px-6">Itens ({itens.length})</h2>
      <ul className="divide-y divide-border md:hidden">
        {itens.map((i) => (
          <li key={i.id} className="space-y-1 px-4 py-3 text-sm">
            <Link href={`/produtos/${i.produto.id}`} className="font-medium hover:text-brand">{i.produto.nome}</Link>
            <p className="tabular-nums text-muted">
              {i.quantidade} × {moeda(i.precoCustoUnitario)} = <strong className="text-foreground">{moeda(i.subtotal)}</strong>
            </p>
            <LotesRecebidos item={i} />
          </li>
        ))}
      </ul>
      <div className="hidden overflow-x-auto md:block">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-border text-xs uppercase tracking-wide text-muted">
            <tr>
              <th scope="col" className="px-6 py-3 font-medium">Produto</th>
              <th scope="col" className="px-6 py-3 text-right font-medium">Qtd.</th>
              <th scope="col" className="px-6 py-3 text-right font-medium">Custo unit.</th>
              <th scope="col" className="px-6 py-3 text-right font-medium">Subtotal</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {itens.map((i) => (
              <tr key={i.id}>
                <td className="px-6 py-3">
                  <Link href={`/produtos/${i.produto.id}`} className="font-medium hover:text-brand">{i.produto.nome}</Link>
                  <LotesRecebidos item={i} />
                </td>
                <td className="px-6 py-3 text-right tabular-nums">{i.quantidade}</td>
                <td className="px-6 py-3 text-right tabular-nums">{moeda(i.precoCustoUnitario)}</td>
                <td className="px-6 py-3 text-right tabular-nums">{moeda(i.subtotal)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="flex items-baseline justify-between border-t border-border px-4 py-4 sm:px-6">
        <p className="text-sm text-muted">Total</p>
        <p className="text-xl font-semibold tabular-nums" data-testid="total-compra">{moeda(total)}</p>
      </div>
    </section>
  );
}

function ReceberForm({ compra, hoje }: { compra: Compra; hoje: string }) {
  const [state, action, pending] = useActionState<FormState, FormData>(receberCompra.bind(null, compra.id), {});
  // Uma grade por item de produto controlado. O estado fica no cliente: se a API recusar, o que foi digitado continua na tela.
  const controlados = (compra.itens ?? []).filter((i) => i.controlaLote);
  const [grades, setGrades] = useState<Record<number, LinhaLote[]>>(() =>
    Object.fromEntries(controlados.map((i) => [i.produto.id, [linhaVazia()]])),
  );
  const [errosLocais, setErrosLocais] = useState<Record<string, string>>({});
  const erros = { ...(state.erros ?? {}), ...errosLocais };
  return (
    <form
      action={action}
      onSubmit={(e) => {
        // Receber nunca passa com lotes que não somam a quantidade comprada.
        const novos: Record<string, string> = {};
        for (const item of controlados) {
          const e2 = validarLinhas(grades[item.produto.id] ?? [], { exigeValidade: item.exigeValidade !== false, esperado: item.quantidade, recebimento: true }, hoje);
          for (const [campo, mensagem] of Object.entries(e2)) novos[`lotes.${item.produto.id}.${campo}`] = mensagem;
        }
        setErrosLocais(novos);
        if (Object.keys(novos).length > 0) {
          e.preventDefault();
          return;
        }
        const data = String(new FormData(e.currentTarget).get("dataEntrega") ?? "");
        const msg = `Registrar o recebimento em ${formatarData(data)}? O estoque de ${compra.itens?.length ?? 0} produto(s) será atualizado e o custo médio recalculado. Esta ação não pode ser desfeita.`;
        if (!window.confirm(msg)) e.preventDefault();
      }}
      className="space-y-3"
    >
      <h3 className="text-sm font-semibold">Receber mercadoria</h3>
      {state.mensagem && <Aviso tipo="erro">{state.mensagem}</Aviso>}
      {controlados.length > 0 && (
        <>
          <input type="hidden" name="lotes" value={JSON.stringify(controlados.map((i) => ({ produtoId: i.produto.id, linhas: grades[i.produto.id] ?? [] })))} />
          <input type="hidden" name="quantidades" value={controlados.map((i) => `${i.produto.id}:${i.quantidade}:${i.exigeValidade === false ? 0 : 1}`).join(",")} />
        </>
      )}
      <Campo
        rotulo="Data de entrega"
        name="dataEntrega"
        type="date"
        required
        min={compra.dataCompra}
        max={hoje}
        defaultValue={state.valores?.dataEntrega ?? hoje}
        erro={state.erros?.dataEntrega}
      />
      <Campo
        rotulo="Vencimento da conta a pagar"
        name="dataVencimento"
        type="date"
        required
        min={compra.dataCompra}
        defaultValue={state.valores?.dataVencimento ?? somarDias(hoje, 30)}
        erro={state.erros?.dataVencimento}
        ajuda="Até 365 dias após a entrega. A saída financeira só acontece no pagamento."
      />
      {controlados.map((item) => {
        const prefixo = `lotes.${item.produto.id}.`;
        const errosDoItem = Object.fromEntries(
          Object.entries(erros)
            .filter(([k]) => k.startsWith(prefixo))
            .map(([k, v]) => [k.slice(prefixo.length), v]),
        );
        return (
          <GradeLotes
            key={item.id}
            prefixo={`rec-${item.produto.id}`}
            linhas={grades[item.produto.id] ?? []}
            onChange={(linhas) => setGrades((g) => ({ ...g, [item.produto.id]: linhas }))}
            esperado={item.quantidade}
            rotuloEsperado={`${item.produto.nome} (${item.quantidade})`}
            exigeValidade={item.exigeValidade !== false}
            erros={errosDoItem}
            minValidade={hoje}
          />
        );
      })}
      <button type="submit" className="botao-primario w-full sm:w-auto" disabled={pending}>
        {pending ? "Registrando…" : "Receber compra"}
      </button>
    </form>
  );
}

function CancelarForm({ compra }: { compra: Compra }) {
  const [state, action, pending] = useActionState<FormState, FormData>(cancelarCompra.bind(null, compra.id), {});
  return (
    <form
      action={action}
      onSubmit={(e) => {
        if (!window.confirm(`Cancelar a compra #${compra.id}? O estoque não é alterado e a compra não poderá mais ser recebida.`)) e.preventDefault();
      }}
      className="space-y-3"
    >
      <h3 className="text-sm font-semibold">Cancelar pedido</h3>
      {state.mensagem && <Aviso tipo="erro">{state.mensagem}</Aviso>}
      <div>
        <label htmlFor="campo-motivo-cancelamento" className="mb-1 block text-sm font-medium">
          Motivo<span className="text-danger"> *</span>
        </label>
        <textarea
          id="campo-motivo-cancelamento"
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
        {pending ? "Cancelando…" : "Cancelar compra"}
      </button>
    </form>
  );
}

export function CompraDetalhes({ compra, podeAdministrar, hoje }: { compra: Compra; podeAdministrar: boolean; hoje: string }) {
  const dados: [string, string][] = [
    ["Fornecedor", compra.fornecedor.nome],
    ["Data da compra", formatarData(compra.dataCompra)],
    ["Data de entrega", formatarData(compra.dataEntrega)],
    ["Pagamento", compra.formaPagamento],
    ["Responsável", compra.funcionario?.nome ?? "—"],
  ];
  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center gap-3">
        <BadgeStatusCompra status={compra.status} />
        {compra.status === "RECEBIDO" && <p className="text-sm text-muted">Estoque e custo médio já atualizados.</p>}
        {compra.contaPagar && (
          <Link href={`/financeiro/contas-a-pagar/${compra.contaPagar.id}`} className="text-sm font-medium text-brand hover:underline">
            Conta a pagar #{compra.contaPagar.id} · {compra.contaPagar.status === "PAGA" ? "paga" : `vence ${formatarData(compra.contaPagar.dataVencimento)}`} →
          </Link>
        )}
        {compra.status === "CANCELADO" && <p className="text-sm text-muted">Compra cancelada: não altera o estoque.</p>}
      </div>

      <dl className="cartao grid gap-4 p-4 sm:grid-cols-3 sm:p-6">
        {dados.map(([rotulo, valor]) => (
          <div key={rotulo}>
            <dt className="text-xs uppercase text-muted">{rotulo}</dt>
            <dd className="font-medium">
              {rotulo === "Fornecedor" ? <Link href={`/fornecedores/${compra.fornecedor.id}`} className="hover:text-brand">{valor}</Link> : valor}
            </dd>
          </div>
        ))}
        {compra.observacoes && (
          <div className="sm:col-span-3">
            <dt className="text-xs uppercase text-muted">Observações</dt>
            <dd className="whitespace-pre-line text-sm">{compra.observacoes}</dd>
          </div>
        )}
      </dl>

      <Itens itens={compra.itens ?? []} total={compra.valorTotal} />

      {compra.status === "PEDIDO" && podeAdministrar && (
        <section className="cartao grid gap-6 p-4 sm:p-6 md:grid-cols-2" aria-label="Ações do pedido">
          <ReceberForm compra={compra} hoje={hoje} />
          <CancelarForm compra={compra} />
        </section>
      )}
    </div>
  );
}
