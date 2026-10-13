"use client";

import { moeda } from "@/lib/agendamentos";
import { formatarDataHora } from "@/lib/datas";
import { rotuloForma, type Venda } from "@/lib/vendas";

/** Comprovante NÃO fiscal (~80 mm). O botão Imprimir some na impressão. */
export function Comprovante({ venda }: { venda: Venda }) {
  const itens = venda.itens ?? [];
  const cancelada = venda.status === "CANCELADA";
  return (
    <div className="comprovante mx-auto w-full max-w-[80mm] space-y-3 bg-white p-4 font-mono text-[12px] leading-snug text-black">
      <header className="text-center">
        <p className="text-sm font-bold">PetConnect</p>
        <p>Comprovante não fiscal</p>
      </header>
      {cancelada && <p className="border-2 border-black py-1 text-center text-base font-bold">CANCELADA</p>}
      <p>
        Venda nº {venda.id}
        <br />
        {formatarDataHora(venda.dataVenda)}
      </p>
      <hr className="border-dashed border-black" />
      <ul className="space-y-1">
        {itens.map((i) => (
          <li key={i.id}>
            <p>{i.produto.nome}</p>
            <p className="flex justify-between">
              <span>{i.quantidade} × {moeda(i.precoUnitario)}</span>
              <span>{moeda(i.subtotal)}</span>
            </p>
          </li>
        ))}
      </ul>
      <hr className="border-dashed border-black" />
      <dl className="space-y-0.5">
        <div className="flex justify-between"><dt>Subtotal</dt><dd>{moeda(venda.subtotal)}</dd></div>
        <div className="flex justify-between"><dt>Desconto</dt><dd>- {moeda(venda.desconto)}</dd></div>
        <div className="flex justify-between text-sm font-bold"><dt>TOTAL</dt><dd>{moeda(venda.valorTotal)}</dd></div>
        <div className="flex justify-between"><dt>Pagamento</dt><dd>{rotuloForma(venda.formaPagamento)}</dd></div>
        {venda.valorRecebido !== null && (
          <>
            <div className="flex justify-between"><dt>Recebido</dt><dd>{moeda(venda.valorRecebido)}</dd></div>
            <div className="flex justify-between"><dt>Troco</dt><dd>{moeda(venda.troco)}</dd></div>
          </>
        )}
      </dl>
      <hr className="border-dashed border-black" />
      <p>Operador: {venda.operador?.nome ?? "—"}</p>
      {venda.cliente && <p>Cliente: {venda.cliente.nome}</p>}
      <p className="text-center">Obrigado pela preferência!</p>
      <button type="button" className="botao-primario w-full print:hidden" onClick={() => window.print()}>
        Imprimir
      </button>
    </div>
  );
}
