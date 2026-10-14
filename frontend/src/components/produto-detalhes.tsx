"use client";

import Link from "next/link";
import { useActionState, useState } from "react";
import * as z from "zod";
import { ajustarEstoque, alternarAtivoProduto } from "@/app/actions/estoque";
import { AlternarAtivo } from "@/components/alternar-ativo";
import { Aviso } from "@/components/aviso";
import { Campo } from "@/components/campo";
import { BadgeAtivo, BadgeEstoque } from "@/components/estoque-badges";
import { moeda } from "@/lib/agendamentos";
import type { FormState } from "@/lib/clientes";
import { formatarInstante } from "@/lib/datas";
import { ajusteEstoqueSchema, rotuloMovimentacao, type Movimentacao, type Produto } from "@/lib/estoque";
import type { Lote } from "@/lib/lotes";

/** Dados somente leitura (SERVICO, ou quem não edita produtos). */
export function ProdutoDados({ produto }: { produto: Produto }) {
  const itens: [string, React.ReactNode][] = [
    ["Categoria", produto.categoria.nome],
    ["Fornecedor padrão", produto.fornecedorPadrao?.nome ?? "—"],
    ["Código de barras", produto.codigoBarras ?? "—"],
    ["Preço de venda", moeda(produto.precoVenda)],
    ["Custo médio", moeda(produto.precoCusto)],
    ["Estoque mínimo", produto.estoqueMinimo],
  ];
  return (
    <dl className="cartao grid gap-4 p-4 sm:grid-cols-3 sm:p-6">
      {itens.map(([rotulo, valor]) => (
        <div key={rotulo}>
          <dt className="text-xs uppercase text-muted">{rotulo}</dt>
          <dd className="font-medium tabular-nums">{valor}</dd>
        </div>
      ))}
      {produto.descricao && (
        <div className="sm:col-span-3">
          <dt className="text-xs uppercase text-muted">Descrição</dt>
          <dd className="whitespace-pre-line text-sm">{produto.descricao}</dd>
        </div>
      )}
    </dl>
  );
}

/** Desativar (ADMIN/VENDAS) e reativar (só ADMIN). A API confere de novo. */
export function ProdutoAcoes({ produto, podeDesativar, podeReativar }: { produto: Produto; podeDesativar: boolean; podeReativar: boolean }) {
  if (produto.ativo && podeDesativar) {
    return (
      <AlternarAtivo
        action={alternarAtivoProduto.bind(null, produto.id, false)}
        ativo
        rotulo="Desativar produto"
        confirmacao={`Desativar ${produto.nome}? Ele sai da busca de vendas e de novas compras. O saldo (${produto.quantidadeEstoque}) e o histórico são preservados.`}
      />
    );
  }
  if (!produto.ativo && podeReativar) {
    return (
      <AlternarAtivo
        action={alternarAtivoProduto.bind(null, produto.id, true)}
        ativo={false}
        rotulo="Reativar produto"
        confirmacao={`Reativar ${produto.nome}?`}
      />
    );
  }
  return null;
}

function Quantidade({ valor }: { valor: number }) {
  return (
    <span className={`font-semibold tabular-nums ${valor > 0 ? "text-brand-strong" : "text-danger"}`}>
      {valor > 0 ? `+${valor}` : `−${Math.abs(valor)}`}
    </span>
  );
}

function Origem({ m }: { m: Movimentacao }) {
  if (m.compraId) return <Link href={`/compras/${m.compraId}`} className="text-brand hover:underline">Compra #{m.compraId}</Link>;
  if (m.vendaId) return <span>Venda #{m.vendaId}</span>;
  return <span className="text-muted">{m.motivo}</span>;
}

export function HistoricoEstoque({ movimentacoes }: { movimentacoes: Movimentacao[] }) {
  if (movimentacoes.length === 0) {
    return <p className="cartao p-8 text-center text-sm text-muted">Nenhuma movimentação de estoque registrada.</p>;
  }
  return (
    <>
      <ul className="space-y-3 md:hidden">
        {movimentacoes.map((m) => (
          <li key={m.id} className="cartao space-y-1 p-4 text-sm">
            <div className="flex items-center justify-between gap-2">
              <p className="font-medium">{rotuloMovimentacao(m.tipo)}</p>
              <Quantidade valor={m.quantidade} />
            </div>
            <p className="tabular-nums text-muted">
              Saldo {m.saldoAnterior} → <strong className="text-foreground">{m.saldoResultante}</strong>
            </p>
            <p><Origem m={m} /></p>
            <p className="text-xs text-muted">{formatarInstante(m.createdAt)} · {m.usuario.nome}</p>
          </li>
        ))}
      </ul>
      <div className="cartao hidden overflow-x-auto md:block">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-border text-xs uppercase tracking-wide text-muted">
            <tr>
              <th scope="col" className="px-4 py-3 font-medium">Data</th>
              <th scope="col" className="px-4 py-3 font-medium">Tipo</th>
              <th scope="col" className="px-4 py-3 text-right font-medium">Qtd.</th>
              <th scope="col" className="px-4 py-3 text-right font-medium">Saldo</th>
              <th scope="col" className="px-4 py-3 font-medium">Origem / motivo</th>
              <th scope="col" className="px-4 py-3 font-medium">Usuário</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {movimentacoes.map((m) => (
              <tr key={m.id}>
                <td className="whitespace-nowrap px-4 py-3 tabular-nums">{formatarInstante(m.createdAt)}</td>
                <td className="px-4 py-3">{rotuloMovimentacao(m.tipo)}</td>
                <td className="px-4 py-3 text-right"><Quantidade valor={m.quantidade} /></td>
                <td className="whitespace-nowrap px-4 py-3 text-right tabular-nums">{m.saldoAnterior} → {m.saldoResultante}</td>
                <td className="px-4 py-3"><Origem m={m} /></td>
                <td className="px-4 py-3">{m.usuario.nome}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

/** Ajuste manual (só ADMIN): positivo entra, negativo sai. Pede confirmação com o saldo resultante. */
export function AjusteEstoqueForm({ produto, lotes = [] }: { produto: Produto; lotes?: Lote[] }) {
  const [state, action, pending] = useActionState<FormState, FormData>(ajustarEstoque.bind(null, produto.id), {});
  const [errosLocais, setErrosLocais] = useState<Record<string, string>>({});
  const controla = produto.controlaLote === true;
  const [loteEscolhido, setLoteEscolhido] = useState("");
  const novoLote = loteEscolhido === "novo";
  const erros = { ...(state.erros ?? {}), ...errosLocais };

  function validar(e: React.FormEvent<HTMLFormElement>) {
    const r = ajusteEstoqueSchema.safeParse(Object.fromEntries(new FormData(e.currentTarget).entries()));
    if (!r.success) {
      e.preventDefault();
      setErrosLocais(
        Object.fromEntries(
          Object.entries(z.flattenError(r.error).fieldErrors).map(([k, v]) => [k, (v as string[] | undefined)?.[0] ?? "Inválido"]),
        ),
      );
      return;
    }
    setErrosLocais({});
    if (controla) {
      // Produto com lote: o ajuste é sempre sobre um lote identificável; saída nunca passa do saldo do lote.
      if (!loteEscolhido) {
        e.preventDefault();
        setErrosLocais({ loteId: "Escolha o lote do ajuste." });
        return;
      }
      if (novoLote && (!r.data.codigoLote || r.data.quantidade < 0)) {
        e.preventDefault();
        setErrosLocais(r.data.quantidade < 0 ? { quantidade: "Lote novo só recebe entrada (quantidade positiva)." } : { codigoLote: "Informe o código do lote novo." });
        return;
      }
      const lote = lotes.find((l) => String(l.id) === loteEscolhido);
      if (lote && lote.saldo + r.data.quantidade < 0) {
        e.preventDefault();
        setErrosLocais({ quantidade: `O lote ${lote.codigoLote} tem ${lote.saldo}: a saída não pode passar disso.` });
        return;
      }
    }
    const resultante = produto.quantidadeEstoque + r.data.quantidade;
    if (resultante < 0) {
      e.preventDefault();
      setErrosLocais({ quantidade: `O saldo atual é ${produto.quantidadeEstoque}: a saída não pode deixá-lo negativo.` });
      return;
    }
    if (!window.confirm(`Confirmar ajuste de ${r.data.quantidade > 0 ? "+" : ""}${r.data.quantidade}? O saldo passará de ${produto.quantidadeEstoque} para ${resultante}.`)) {
      e.preventDefault();
    }
  }

  return (
    <form action={action} onSubmit={validar} className="cartao grid gap-4 p-4 sm:grid-cols-[10rem_1fr_auto] sm:items-start sm:p-6" noValidate>
      <h3 className="text-sm font-semibold text-muted sm:col-span-3">Ajustar estoque</h3>
      {state.mensagem && (
        <div className="sm:col-span-3">
          <Aviso tipo="erro">{state.mensagem}</Aviso>
        </div>
      )}
      {controla && (
        <div className="sm:col-span-3 grid gap-4 sm:grid-cols-[1fr_1fr_11rem]">
          <div>
            <label htmlFor="campo-loteId" className="mb-1 block text-sm font-medium">Lote<span className="text-danger"> *</span></label>
            <select
              id="campo-loteId"
              name={novoLote ? "loteNovo" : "loteId"}
              className="campo"
              value={loteEscolhido}
              onChange={(e) => setLoteEscolhido(e.target.value)}
              aria-invalid={erros.loteId ? true : undefined}
              aria-describedby={erros.loteId ? "campo-loteId-erro" : undefined}
            >
              <option value="">Selecione o lote</option>
              {lotes.map((l) => (
                <option key={l.id} value={l.id}>
                  {l.codigoLote} · saldo {l.saldo}
                </option>
              ))}
              <option value="novo">+ Lote novo (só entrada)</option>
            </select>
            {erros.loteId && <p id="campo-loteId-erro" className="mt-1 text-xs text-danger">{erros.loteId}</p>}
          </div>
          {novoLote && (
            <>
              <Campo rotulo="Código do lote novo" name="codigoLote" maxLength={50} defaultValue={state.valores?.codigoLote ?? ""} erro={erros.codigoLote} required />
              <Campo rotulo="Validade" name="validade" type="date" defaultValue={state.valores?.validade ?? ""} erro={erros.validade} required={!produto.permiteSemValidade} />
            </>
          )}
        </div>
      )}
      <Campo rotulo="Quantidade" name="quantidade" inputMode="numeric" placeholder="Ex.: 5 ou -3" defaultValue={state.valores?.quantidade ?? ""} erro={erros.quantidade} required />
      <Campo rotulo="Motivo" name="motivo" maxLength={500} placeholder="Ex.: contagem física, avaria" defaultValue={state.valores?.motivo ?? ""} erro={erros.motivo} required />
      <button type="submit" className="botao-primario sm:mt-6" disabled={pending}>
        {pending ? "Ajustando…" : "Ajustar estoque"}
      </button>
    </form>
  );
}

export function ResumoEstoque({ produto }: { produto: Produto }) {
  return (
    <div className="cartao flex flex-wrap items-center gap-x-8 gap-y-3 p-4 sm:p-6">
      <div>
        <p className="text-xs uppercase text-muted">Saldo atual</p>
        <p className="text-3xl font-semibold tabular-nums">{produto.quantidadeEstoque}</p>
      </div>
      <div>
        <p className="text-xs uppercase text-muted">Estoque mínimo</p>
        <p className="text-3xl font-semibold tabular-nums text-muted">{produto.estoqueMinimo}</p>
      </div>
      <BadgeEstoque abaixoDoMinimo={produto.abaixoDoMinimo} />
      {!produto.ativo && <BadgeAtivo ativo={false} />}
    </div>
  );
}
