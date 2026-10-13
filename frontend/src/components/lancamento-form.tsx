"use client";

import Link from "next/link";
import { useActionState, useState } from "react";
import * as z from "zod";
import { criarLancamento } from "@/app/actions/financeiro";
import { Aviso } from "@/components/aviso";
import { Campo } from "@/components/campo";
import type { FormState } from "@/lib/clientes";
import { CATEGORIAS_MANUAIS, lancamentoManualSchema, MENSAGEM_SEM_CAIXA } from "@/lib/financeiro";
import { FORMAS_PAGAMENTO, novaChave } from "@/lib/vendas";

/**
 * Lançamento manual (só ADMIN). A Idempotency-Key é gerada UMA vez ao montar e
 * reenviada em qualquer retry: um duplo clique ou reenvio não duplica o lançamento.
 */
export function LancamentoForm({ caixaAberto }: { caixaAberto: boolean }) {
  const [chave] = useState(novaChave);
  const [state, action, pending] = useActionState<FormState, FormData>(criarLancamento, {});
  const [errosLocais, setErrosLocais] = useState<Record<string, string>>({});
  const erros = { ...(state.erros ?? {}), ...errosLocais };
  const valor = (campo: string, padrao = "") => state.valores?.[campo] ?? padrao;

  function validar(e: React.FormEvent<HTMLFormElement>) {
    const r = lancamentoManualSchema.safeParse(Object.fromEntries(new FormData(e.currentTarget).entries()));
    if (r.success) {
      setErrosLocais({});
      return;
    }
    e.preventDefault();
    setErrosLocais(
      Object.fromEntries(Object.entries(z.flattenError(r.error).fieldErrors).map(([k, v]) => [k, (v as string[] | undefined)?.[0] ?? "Inválido"])),
    );
  }

  const select = (id: string, nome: string, rotulo: string, opcoes: { valor: string; rotulo: string }[]) => (
    <div>
      <label htmlFor={id} className="mb-1 block text-sm font-medium">
        {rotulo}<span className="text-danger"> *</span>
      </label>
      {/* key: remonta com o valor devolvido pela action (o reset do React 19 não reaplica defaultValue em select). */}
      <select key={valor(nome)} id={id} name={nome} className="campo" defaultValue={valor(nome)} aria-invalid={erros[nome] ? true : undefined}>
        <option value="">Selecione…</option>
        {opcoes.map((o) => (
          <option key={o.valor} value={o.valor}>{o.rotulo}</option>
        ))}
      </select>
      {erros[nome] && <p className="mt-1 text-xs text-danger">{erros[nome]}</p>}
    </div>
  );

  return (
    <form action={action} onSubmit={validar} className="space-y-6" noValidate>
      <input type="hidden" name="chave" value={chave} />
      {!caixaAberto && (
        <Aviso tipo="erro">
          {MENSAGEM_SEM_CAIXA}{" "}
          <Link href="/caixa" className="font-medium underline">Abrir caixa</Link>
        </Aviso>
      )}
      {state.mensagem && <Aviso tipo="erro">{state.mensagem}</Aviso>}
      <fieldset className="cartao grid gap-4 p-4 sm:grid-cols-2 sm:p-6">
        <legend className="px-1 text-sm font-semibold text-muted">Lançamento</legend>
        {select("lanc-tipo", "tipo", "Tipo", [
          { valor: "SAIDA", rotulo: "Saída (despesa)" },
          { valor: "ENTRADA", rotulo: "Entrada" },
        ])}
        {select("lanc-categoria", "categoria", "Categoria", CATEGORIAS_MANUAIS)}
        <Campo className="sm:col-span-2" rotulo="Descrição" name="descricao" required maxLength={100} defaultValue={valor("descricao")} erro={erros.descricao} />
        <Campo rotulo="Valor (R$)" name="valor" required inputMode="decimal" placeholder="0,00" defaultValue={valor("valor")} erro={erros.valor} />
        {select("lanc-forma", "formaPagamento", "Forma de pagamento", FORMAS_PAGAMENTO)}
        <div className="sm:col-span-2">
          <label htmlFor="lanc-obs" className="mb-1 block text-sm font-medium">Observações</label>
          <textarea id="lanc-obs" name="observacoes" rows={2} maxLength={2000} className="campo" defaultValue={valor("observacoes")} />
        </div>
        <p className="text-sm text-muted sm:col-span-2">
          O lançamento entra no seu caixa aberto e não pode ser editado: para corrigir, estorne-o depois.
        </p>
      </fieldset>
      <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        <Link href="/financeiro" className="botao-secundario">Cancelar</Link>
        <button type="submit" className="botao-primario" disabled={pending || !caixaAberto}>
          {pending ? "Salvando…" : "Registrar lançamento"}
        </button>
      </div>
    </form>
  );
}
