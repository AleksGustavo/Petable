"use client";

import { moedaCentavos, paraCentavos } from "@/lib/estoque";
import type { Papel } from "@/lib/jwt";
import {
  descontoCentavos,
  FORMAS_PAGAMENTO,
  TETO_DESCONTO_VENDAS,
  trocoCentavos,
  type FormaPagamento,
  type ModoDesconto,
} from "@/lib/vendas";

export type Pagamento = { modoDesconto: ModoDesconto; desconto: string; forma: FormaPagamento | null; recebido: string };

export const PAGAMENTO_INICIAL: Pagamento = { modoDesconto: "valor", desconto: "", forma: null, recebido: "" };

/** Totais do pagamento em centavos (visual; a prévia do backend confirma antes de finalizar). */
export function calcularPagamento(p: Pagamento, subtotalCentavos: number) {
  const desconto = descontoCentavos(p.modoDesconto, p.desconto, subtotalCentavos);
  const total = Number.isNaN(desconto) ? subtotalCentavos : subtotalCentavos - desconto;
  const recebido = p.forma === "DINHEIRO" ? paraCentavos(p.recebido) : null;
  return { desconto, total, recebido, troco: trocoCentavos(recebido, total) };
}

/**
 * Desconto (R$ ou %), forma de pagamento, valor recebido/troco e o botão Finalizar.
 * A confirmação (com o total do backend) fica no PDV. Erros locais aqui são só de UX: a API valida tudo.
 */
export function PdvPagamento({
  papel,
  subtotalCentavos,
  valor,
  onChange,
  onFinalizar,
  pendente,
  erros = {},
  vazio,
}: {
  papel: Papel;
  subtotalCentavos: number;
  valor: Pagamento;
  onChange: (p: Pagamento) => void;
  onFinalizar: () => void;
  pendente: boolean;
  erros?: Record<string, string>;
  vazio: boolean;
}) {
  const { desconto, total, recebido, troco } = calcularPagamento(valor, subtotalCentavos);
  const set = (parcial: Partial<Pagamento>) => onChange({ ...valor, ...parcial });
  const tetoVendas = papel === "VENDAS";
  const problemaDesconto = Number.isNaN(desconto)
    ? "Desconto inválido"
    : desconto > 0 && desconto >= subtotalCentavos
      ? "O desconto deve ser menor que o subtotal."
      : tetoVendas && desconto > Math.round((subtotalCentavos * TETO_DESCONTO_VENDAS) / 100)
        ? `Desconto acima do permitido para o seu perfil (máximo ${TETO_DESCONTO_VENDAS}%).`
        : null;
  const problemaRecebido =
    valor.forma === "DINHEIRO" && recebido !== null && (Number.isNaN(recebido) || recebido < total)
      ? "O valor recebido não pode ser menor que o total."
      : null;
  const erroDesconto = problemaDesconto ?? erros.desconto;
  const erroRecebido = problemaRecebido ?? erros.valorRecebido;
  const podeFinalizar = !vazio && valor.forma !== null && !problemaDesconto && !problemaRecebido && !pendente;

  return (
    <div className="space-y-5">
      <fieldset className="space-y-2">
        <legend className="text-sm font-medium">Desconto</legend>
        <div className="flex gap-2">
          <div className="flex overflow-hidden rounded-lg border border-border" role="radiogroup" aria-label="Tipo de desconto">
            {(["valor", "percentual"] as const).map((m) => (
              <button
                key={m}
                type="button"
                role="radio"
                aria-checked={valor.modoDesconto === m}
                className={`px-3 text-sm font-medium ${valor.modoDesconto === m ? "bg-brand text-white" : "hover:bg-brand-soft"}`}
                onClick={() => set({ modoDesconto: m, desconto: "" })}
              >
                {m === "valor" ? "R$" : "%"}
              </button>
            ))}
          </div>
          <input
            className="campo flex-1 tabular-nums"
            inputMode="decimal"
            aria-label={valor.modoDesconto === "valor" ? "Desconto em reais" : "Desconto em porcentagem"}
            placeholder={valor.modoDesconto === "valor" ? "0,00" : "0"}
            value={valor.desconto}
            onChange={(e) => set({ desconto: e.target.value })}
            aria-invalid={erroDesconto ? true : undefined}
          />
        </div>
        {tetoVendas && <p className="text-xs text-muted">Seu perfil pode dar no máximo {TETO_DESCONTO_VENDAS}% de desconto.</p>}
        {erroDesconto && <p className="text-xs text-danger">{erroDesconto}</p>}
      </fieldset>

      <fieldset className="space-y-2">
        <legend className="text-sm font-medium">Forma de pagamento</legend>
        <div className="grid grid-cols-2 gap-2" role="radiogroup" aria-label="Forma de pagamento">
          {FORMAS_PAGAMENTO.map((f) => (
            <button
              key={f.valor}
              type="button"
              role="radio"
              aria-checked={valor.forma === f.valor}
              onClick={() => set({ forma: f.valor })}
              className={`rounded-lg border px-3 py-3 text-sm font-medium transition ${
                valor.forma === f.valor ? "border-brand bg-brand text-white" : "border-border hover:border-brand"
              } ${f.valor === "TRANSFERENCIA" ? "col-span-2" : ""}`}
            >
              {f.rotulo}
            </button>
          ))}
        </div>
        {erros.formaPagamento && <p className="text-xs text-danger">{erros.formaPagamento}</p>}
      </fieldset>

      {valor.forma === "DINHEIRO" && (
        <div className="space-y-1">
          <label htmlFor="pdv-recebido" className="block text-sm font-medium">
            Valor recebido (R$)<span className="text-danger"> *</span>
          </label>
          <input
            id="pdv-recebido"
            className="campo tabular-nums"
            inputMode="decimal"
            placeholder="0,00"
            value={valor.recebido}
            onChange={(e) => set({ recebido: e.target.value })}
            aria-invalid={erroRecebido ? true : undefined}
          />
          {erroRecebido && <p className="text-xs text-danger">{erroRecebido}</p>}
          {troco !== null && !Number.isNaN(troco) && troco >= 0 && (
            <p className="text-sm">
              Troco: <strong className="tabular-nums" data-testid="troco">{moedaCentavos(troco)}</strong>
            </p>
          )}
        </div>
      )}

      <dl className="space-y-1 border-t border-border pt-4 text-sm">
        <div className="flex justify-between">
          <dt className="text-muted">Subtotal</dt>
          <dd className="tabular-nums">{moedaCentavos(subtotalCentavos)}</dd>
        </div>
        <div className="flex justify-between">
          <dt className="text-muted">Desconto</dt>
          <dd className="tabular-nums">− {moedaCentavos(Number.isNaN(desconto) ? 0 : desconto)}</dd>
        </div>
        <div className="flex items-baseline justify-between pt-1">
          <dt className="font-medium">Total</dt>
          <dd className="text-2xl font-semibold tabular-nums" data-testid="total-venda">{moedaCentavos(total)}</dd>
        </div>
      </dl>

      <button
        type="button"
        className="botao-primario w-full py-3 text-base"
        disabled={!podeFinalizar}
        onClick={onFinalizar}
      >
        {pendente ? "Finalizando…" : "Finalizar venda"}
      </button>
    </div>
  );
}
