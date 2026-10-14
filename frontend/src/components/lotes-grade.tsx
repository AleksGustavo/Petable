"use client";

import { Campo } from "@/components/campo";
import { conferirSoma, linhaVazia, mensagemSoma, type LinhaLote } from "@/lib/lotes";

type Props = {
  linhas: LinhaLote[];
  onChange: (linhas: LinhaLote[]) => void;
  /** Prefixo único dos ids e nomes dos campos (várias grades na mesma página). */
  prefixo: string;
  /** Quantidade que os lotes precisam somar. */
  esperado: number;
  rotuloEsperado: string;
  exigeValidade: boolean;
  /** Erros por campo ("0.codigo", "1.quantidade", "_") vindos da validação. */
  erros?: Record<string, string>;
  /** Menor validade aceita (recebimento não aceita lote vencido). */
  minValidade?: string;
};

/**
 * Grade de lotes: código do fabricante, validade e quantidade por linha. A soma é conferida a cada digitação, com texto
 * ("confere", "faltam N"), então o operador vê a divergência antes de confirmar e sem depender de cor.
 */
export function GradeLotes({ linhas, onChange, prefixo, esperado, rotuloEsperado, exigeValidade, erros = {}, minValidade }: Props) {
  const c = conferirSoma(linhas, esperado);

  function alterar(i: number, campo: keyof LinhaLote, valor: string) {
    onChange(linhas.map((l, j) => (j === i ? { ...l, [campo]: valor } : l)));
  }

  return (
    <fieldset className="space-y-3">
      <legend className="text-sm font-medium">Lotes recebidos de {rotuloEsperado}</legend>
      {linhas.length === 0 && <p className="text-sm text-muted">Nenhum lote informado. Adicione ao menos um.</p>}
      <ul className="space-y-3">
        {linhas.map((l, i) => (
          <li key={i} className="grid gap-3 rounded-lg border border-border p-3 sm:grid-cols-[1fr_11rem_8rem_auto] sm:items-start">
            <Campo
              rotulo={`Código do lote ${i + 1}`}
              name={`${prefixo}.${i}.codigo`}
              id={`${prefixo}-${i}-codigo`}
              maxLength={50}
              value={l.codigo}
              onChange={(e) => alterar(i, "codigo", e.target.value)}
              erro={erros[`${i}.codigo`]}
              placeholder="Como está na embalagem"
              required
            />
            <Campo
              rotulo="Validade"
              name={`${prefixo}.${i}.validade`}
              id={`${prefixo}-${i}-validade`}
              type="date"
              min={minValidade}
              value={l.validade}
              onChange={(e) => alterar(i, "validade", e.target.value)}
              erro={erros[`${i}.validade`]}
              required={exigeValidade}
            />
            <Campo
              rotulo="Quantidade"
              name={`${prefixo}.${i}.quantidade`}
              id={`${prefixo}-${i}-quantidade`}
              inputMode="numeric"
              value={l.quantidade}
              onChange={(e) => alterar(i, "quantidade", e.target.value)}
              erro={erros[`${i}.quantidade`]}
              required
            />
            <button
              type="button"
              className="botao-secundario py-2 text-sm sm:mt-6"
              onClick={() => onChange(linhas.filter((_, j) => j !== i))}
              aria-label={`Remover lote ${i + 1}`}
            >
              Remover
            </button>
          </li>
        ))}
      </ul>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <button type="button" className="botao-secundario text-sm" onClick={() => onChange([...linhas, linhaVazia()])}>
          + Adicionar lote
        </button>
        <p role="status" aria-live="polite" className={`text-sm font-medium ${c.confere ? "text-brand-strong" : "text-danger"}`}>
          <span aria-hidden="true">{c.confere ? "✓ " : "⚠ "}</span>
          {mensagemSoma(c)}
        </p>
      </div>
      {erros._ && !c.confere && <p className="text-xs text-danger">{erros._}</p>}
    </fieldset>
  );
}
