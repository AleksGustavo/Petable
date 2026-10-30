"use client";

import Link from "next/link";
import { useActionState, useEffect, useState } from "react";
import { criarCompra } from "@/app/actions/estoque";
import { Aviso } from "@/components/aviso";
import { Campo } from "@/components/campo";
import type { FormState } from "@/lib/clientes";
import {
  compraSchema,
  errosPorCaminho,
  moedaCentavos,
  subtotalCentavos,
  totalCentavos,
  type FornecedorOpcao,
  type ItemCompraForm,
  type ProdutoRapido,
} from "@/lib/estoque";
import type { FuncionarioResumo } from "@/lib/usuarios";

export const MAX_ITENS = 100;

async function buscarNaApi(q: string): Promise<ProdutoRapido[]> {
  const r = await fetch(`/api/produtos/busca?${new URLSearchParams({ q })}`);
  return r.ok ? r.json() : [];
}

/** Busca de produto (nome ou código) para adicionar à compra. Produtos já adicionados ficam bloqueados. */
function BuscaProduto({
  jaAdicionados,
  onAdicionar,
  buscar,
  desabilitado,
}: {
  jaAdicionados: Set<number>;
  onAdicionar: (p: ProdutoRapido) => void;
  buscar: (q: string) => Promise<ProdutoRapido[]>;
  desabilitado: boolean;
}) {
  const [termo, setTermo] = useState("");
  const [resultados, setResultados] = useState<ProdutoRapido[]>([]);
  const [buscando, setBuscando] = useState(false);

  useEffect(() => {
    const q = termo.trim();
    if (q.length < 2) return;
    let cancelado = false;
    const t = setTimeout(async () => {
      setBuscando(true);
      try {
        const lista = await buscar(q);
        if (!cancelado) setResultados(lista);
      } finally {
        if (!cancelado) setBuscando(false);
      }
    }, 250);
    return () => {
      cancelado = true;
      clearTimeout(t);
    };
  }, [termo, buscar]);

  const visiveis = termo.trim().length < 2 ? [] : resultados;

  return (
    <div className="space-y-2">
      <label htmlFor="busca-produto-compra" className="block text-sm font-medium">Adicionar produto</label>
      <input
        id="busca-produto-compra"
        type="search"
        className="campo"
        placeholder="Digite nome ou código de barras (mín. 2 caracteres)"
        value={termo}
        onChange={(e) => setTermo(e.target.value)}
        disabled={desabilitado}
        autoComplete="off"
      />
      {desabilitado && <p className="text-xs text-muted">Limite de {MAX_ITENS} itens atingido.</p>}
      {buscando && <p className="text-xs text-muted" role="status">Buscando…</p>}
      {!buscando && termo.trim().length >= 2 && visiveis.length === 0 && (
        <p className="text-xs text-muted">Nenhum produto ativo encontrado.</p>
      )}
      {visiveis.length > 0 && (
        <ul className="divide-y divide-border rounded-lg border border-border" aria-label="Produtos encontrados">
          {visiveis.map((p) => {
            const repetido = jaAdicionados.has(p.id);
            return (
              <li key={p.id} className="flex items-center justify-between gap-3 px-3 py-2 text-sm">
                <span className="min-w-0">
                  <span className="block truncate font-medium">{p.nome}</span>
                  <span className="text-xs text-muted tabular-nums">
                    {p.codigoBarras ?? "sem código"} · saldo {p.quantidadeEstoque}
                  </span>
                </span>
                <button
                  type="button"
                  className="botao-secundario shrink-0 px-3 py-1 text-xs"
                  disabled={repetido}
                  onClick={() => {
                    onAdicionar(p);
                    setTermo("");
                    setResultados([]);
                  }}
                  aria-label={repetido ? `${p.nome} já adicionado` : `Adicionar ${p.nome}`}
                >
                  {repetido ? "Já adicionado" : "Adicionar"}
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

/**
 * Novo pedido de compra. Subtotal e total aqui são só visuais (em centavos,
 * sem erro de ponto flutuante); a API recalcula tudo e é a fonte da verdade.
 */
export function CompraForm({
  fornecedores,
  funcionarios,
  hoje,
  buscarProdutos = buscarNaApi,
}: {
  fornecedores: FornecedorOpcao[];
  funcionarios: FuncionarioResumo[];
  hoje: string;
  buscarProdutos?: (q: string) => Promise<ProdutoRapido[]>;
}) {
  const [state, action, pending] = useActionState<FormState, FormData>(criarCompra, {});
  const [itens, setItens] = useState<ItemCompraForm[]>(() => {
    try {
      return state.valores?.itens ? (JSON.parse(state.valores.itens) as ItemCompraForm[]) : [];
    } catch {
      return [];
    }
  });
  const [errosLocais, setErrosLocais] = useState<Record<string, string>>({});
  const [aviso, setAviso] = useState<string | null>(null);
  const erros = { ...(state.erros ?? {}), ...errosLocais };
  const valor = (campo: string, padrao: string) => state.valores?.[campo] ?? padrao;
  // Nos <select>, key = valor devolvido: remonta o campo, pois o reset do React 19 não reaplica defaultValue em select.
  const adicionados = new Set(itens.map((i) => i.produtoId));

  function adicionar(p: ProdutoRapido) {
    if (adicionados.has(p.id)) {
      setAviso(`${p.nome} já está na compra: altere a quantidade na linha existente.`);
      return;
    }
    setAviso(null);
    setItens((atual) => [...atual, { produtoId: p.id, nome: p.nome, quantidade: "1", precoCustoUnitario: "" }]);
  }

  function alterar(produtoId: number, campo: "quantidade" | "precoCustoUnitario", valorNovo: string) {
    setItens((atual) => atual.map((i) => (i.produtoId === produtoId ? { ...i, [campo]: valorNovo } : i)));
  }

  function remover(produtoId: number) {
    setItens((atual) => atual.filter((i) => i.produtoId !== produtoId));
  }

  function validar(e: React.FormEvent<HTMLFormElement>) {
    const dados = Object.fromEntries(new FormData(e.currentTarget).entries());
    const r = compraSchema.safeParse({ ...dados, itens });
    if (r.success) {
      setErrosLocais({});
      return;
    }
    e.preventDefault();
    setErrosLocais(errosPorCaminho(r.error));
  }

  const total = totalCentavos(itens);

  return (
    <form action={action} onSubmit={validar} className="space-y-6" noValidate>
      {state.mensagem && <Aviso tipo="erro">{state.mensagem}</Aviso>}
      <input type="hidden" name="itens" value={JSON.stringify(itens)} />

      <fieldset className="cartao grid gap-4 p-4 sm:grid-cols-2 sm:p-6">
        <legend className="px-1 text-sm font-semibold text-muted">Pedido</legend>
        <div>
          <label htmlFor="campo-fornecedorId" className="mb-1 block text-sm font-medium">
            Fornecedor<span className="text-danger"> *</span>
          </label>
          <select key={valor("fornecedorId", "")} id="campo-fornecedorId" name="fornecedorId" className="campo" defaultValue={valor("fornecedorId", "")} aria-invalid={erros.fornecedorId ? true : undefined}>
            <option value="">Selecione…</option>
            {fornecedores.map((f) => (
              <option key={f.id} value={f.id}>{f.nomeFantasia}</option>
            ))}
          </select>
          {erros.fornecedorId && <p className="mt-1 text-xs text-danger">{erros.fornecedorId}</p>}
          {fornecedores.length === 0 && <p className="mt-1 text-xs text-muted">Nenhum fornecedor ativo. Cadastre um antes.</p>}
        </div>
        <div>
          <label htmlFor="campo-funcionarioId" className="mb-1 block text-sm font-medium">Responsável</label>
          <select key={valor("funcionarioId", "")} id="campo-funcionarioId" name="funcionarioId" className="campo" defaultValue={valor("funcionarioId", "")}>
            <option value="">Não informado</option>
            {funcionarios.map((f) => (
              <option key={f.id} value={f.id}>{f.nome}</option>
            ))}
          </select>
          {erros.funcionarioId && <p className="mt-1 text-xs text-danger">{erros.funcionarioId}</p>}
        </div>
        <Campo rotulo="Data da compra" name="dataCompra" type="date" required max={hoje} defaultValue={valor("dataCompra", hoje)} erro={erros.dataCompra} />
        <Campo rotulo="Forma de pagamento" name="formaPagamento" required maxLength={50} placeholder="Ex.: Boleto 30 dias" defaultValue={valor("formaPagamento", "")} erro={erros.formaPagamento} />
        <div className="sm:col-span-2">
          <label htmlFor="campo-observacoes-compra" className="mb-1 block text-sm font-medium">Observações</label>
          <textarea id="campo-observacoes-compra" name="observacoes" rows={2} maxLength={2000} className="campo" defaultValue={valor("observacoes", "")} />
        </div>
      </fieldset>

      <fieldset className="cartao space-y-4 p-4 sm:p-6">
        <legend className="px-1 text-sm font-semibold text-muted">Itens</legend>
        <BuscaProduto jaAdicionados={adicionados} onAdicionar={adicionar} buscar={buscarProdutos} desabilitado={itens.length >= MAX_ITENS} />
        {aviso && <Aviso tipo="erro">{aviso}</Aviso>}
        {erros.itens && !Object.keys(erros).some((k) => k.startsWith("itens.")) && <p className="text-sm text-danger">{erros.itens}</p>}

        {itens.length === 0 ? (
          <p className="rounded-lg border border-dashed border-border p-6 text-center text-sm text-muted">Nenhum item adicionado.</p>
        ) : (
          <ul className="space-y-3" aria-label="Itens da compra">
            {itens.map((item, i) => (
              <li key={item.produtoId} className="grid gap-3 rounded-lg border border-border p-3 sm:grid-cols-[1fr_7rem_9rem_8rem_auto] sm:items-start">
                <div className="min-w-0">
                  <p className="font-medium">{item.nome}</p>
                  {erros[`itens.${i}.produtoId`] && <p className="text-xs text-danger">{erros[`itens.${i}.produtoId`]}</p>}
                </div>
                <Campo
                  id={`item-${item.produtoId}-quantidade`}
                  name={`item-quantidade-${i}`}
                  rotulo="Quantidade"
                  inputMode="numeric"
                  value={item.quantidade}
                  onChange={(e) => alterar(item.produtoId, "quantidade", e.target.value.replace(/\D/g, ""))}
                  erro={erros[`itens.${i}.quantidade`]}
                />
                <Campo
                  id={`item-${item.produtoId}-custo`}
                  name={`item-custo-${i}`}
                  rotulo="Custo unit. (R$)"
                  inputMode="decimal"
                  value={item.precoCustoUnitario}
                  onChange={(e) => alterar(item.produtoId, "precoCustoUnitario", e.target.value)}
                  erro={erros[`itens.${i}.precoCustoUnitario`]}
                />
                <div>
                  <p className="mb-1 text-sm font-medium">Subtotal</p>
                  <p className="py-2 font-semibold tabular-nums" data-testid={`subtotal-${item.produtoId}`}>
                    {moedaCentavos(subtotalCentavos(item.quantidade, item.precoCustoUnitario))}
                  </p>
                </div>
                <button type="button" className="botao-secundario sm:mt-6" onClick={() => remover(item.produtoId)} aria-label={`Remover ${item.nome}`}>
                  Remover
                </button>
              </li>
            ))}
          </ul>
        )}

        <div className="flex items-baseline justify-between border-t border-border pt-4">
          <p className="text-sm text-muted">
            {itens.length} {itens.length === 1 ? "item" : "itens"} · total estimado (a API recalcula)
          </p>
          <p className="text-xl font-semibold tabular-nums" data-testid="total-compra">{moedaCentavos(total)}</p>
        </div>
      </fieldset>

      <p className="text-sm text-muted">
        O pedido não altera o estoque. O saldo e o custo médio só mudam quando o administrador registrar o recebimento.
      </p>

      <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        <Link href="/compras" className="botao-secundario">Cancelar</Link>
        <button type="submit" className="botao-primario" disabled={pending}>
          {pending ? "Salvando…" : "Registrar pedido"}
        </button>
      </div>
    </form>
  );
}
