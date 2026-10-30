"use client";

import Link from "next/link";
import { useActionState, useState } from "react";
import * as z from "zod";
import { salvarProduto } from "@/app/actions/estoque";
import { Aviso } from "@/components/aviso";
import { Campo } from "@/components/campo";
import { moeda } from "@/lib/agendamentos";
import type { FormState } from "@/lib/clientes";
import { produtoAtualizacaoSchema, produtoSchema, type CategoriaProduto, type FornecedorOpcao, type Produto } from "@/lib/estoque";

const decimal = (v: number | null | undefined) => (v == null ? "" : v.toFixed(2).replace(".", ","));

/**
 * Cadastro/edição de produto. Não existe campo de saldo: o produto nasce com
 * estoque zero e o saldo só muda por compra recebida ou ajuste do ADMIN. O custo
 * só é informado no cadastro; depois vira custo médio das compras.
 */
export function ProdutoForm({
  produto,
  categorias,
  fornecedores,
}: {
  produto?: Produto;
  categorias: CategoriaProduto[];
  fornecedores: FornecedorOpcao[];
}) {
  const [state, action, pending] = useActionState<FormState, FormData>(salvarProduto.bind(null, produto?.id ?? null), {});
  const [errosLocais, setErrosLocais] = useState<Record<string, string>>({});
  const erros = { ...(state.erros ?? {}), ...errosLocais };
  const valor = (campo: string, padrao: string) => state.valores?.[campo] ?? padrao;
  // Nos <select>, key = valor devolvido: remonta o campo, pois o reset do React 19 não reaplica defaultValue em select.
  // Fornecedor padrão inativo continua visível na edição, mas não é oferecido para novos vínculos.
  const opcoesFornecedor =
    produto?.fornecedorPadrao && !fornecedores.some((f) => f.id === produto.fornecedorPadrao?.id)
      ? [{ id: produto.fornecedorPadrao.id, nomeFantasia: `${produto.fornecedorPadrao.nome} (inativo)`, cnpj: "" }, ...fornecedores]
      : fornecedores;

  function validar(e: React.FormEvent<HTMLFormElement>) {
    const dados = Object.fromEntries(new FormData(e.currentTarget).entries());
    const r = (produto ? produtoAtualizacaoSchema : produtoSchema).safeParse(dados);
    if (r.success) {
      setErrosLocais({});
      return;
    }
    e.preventDefault();
    setErrosLocais(
      Object.fromEntries(
        Object.entries(z.flattenError(r.error).fieldErrors).map(([k, v]) => [k, (v as string[] | undefined)?.[0] ?? "Inválido"]),
      ),
    );
  }

  return (
    <form action={action} onSubmit={validar} className="space-y-6" noValidate>
      {state.mensagem && <Aviso tipo="erro">{state.mensagem}</Aviso>}
      <fieldset className="cartao grid gap-4 p-4 sm:grid-cols-2 sm:p-6">
        <legend className="px-1 text-sm font-semibold text-muted">Produto</legend>
        <Campo className="sm:col-span-2" rotulo="Nome" name="nome" required maxLength={100} defaultValue={valor("nome", produto?.nome ?? "")} erro={erros.nome} />
        <div>
          <label htmlFor="campo-categoriaId" className="mb-1 block text-sm font-medium">
            Categoria<span className="text-danger"> *</span>
          </label>
          <select
            key={valor("categoriaId", "")}
            id="campo-categoriaId"
            name="categoriaId"
            className="campo"
            defaultValue={valor("categoriaId", produto ? String(produto.categoria.id) : "")}
            aria-invalid={erros.categoriaId ? true : undefined}
          >
            <option value="">Selecione…</option>
            {categorias.map((c) => (
              <option key={c.id} value={c.id}>{c.nome}</option>
            ))}
          </select>
          {erros.categoriaId && <p className="mt-1 text-xs text-danger">{erros.categoriaId}</p>}
        </div>
        <div>
          <label htmlFor="campo-fornecedorPadraoId" className="mb-1 block text-sm font-medium">Fornecedor padrão</label>
          <select
            key={valor("fornecedorPadraoId", "")}
            id="campo-fornecedorPadraoId"
            name="fornecedorPadraoId"
            className="campo"
            defaultValue={valor("fornecedorPadraoId", produto?.fornecedorPadrao ? String(produto.fornecedorPadrao.id) : "")}
            aria-invalid={erros.fornecedorPadraoId ? true : undefined}
          >
            <option value="">Nenhum</option>
            {opcoesFornecedor.map((f) => (
              <option key={f.id} value={f.id}>{f.nomeFantasia}</option>
            ))}
          </select>
          {erros.fornecedorPadraoId && <p className="mt-1 text-xs text-danger">{erros.fornecedorPadraoId}</p>}
        </div>
        <Campo
          rotulo="Código de barras"
          name="codigoBarras"
          inputMode="numeric"
          maxLength={14}
          defaultValue={valor("codigoBarras", produto?.codigoBarras ?? "")}
          erro={erros.codigoBarras}
          ajuda="Opcional: 8 a 14 dígitos (EAN/GTIN)."
        />
        <Campo
          rotulo="Estoque mínimo"
          name="estoqueMinimo"
          required
          inputMode="numeric"
          defaultValue={valor("estoqueMinimo", produto ? String(produto.estoqueMinimo) : "0")}
          erro={erros.estoqueMinimo}
          ajuda="Alerta quando o saldo ficar igual ou abaixo deste valor."
        />
        {produto ? (
          <div>
            <p className="mb-1 text-sm font-medium">Custo médio</p>
            <p className="campo bg-border/20 tabular-nums">{moeda(produto.precoCusto)}</p>
            <p className="mt-1 text-xs text-muted">Atualizado automaticamente pelas compras recebidas.</p>
          </div>
        ) : (
          <Campo
            rotulo="Custo inicial (R$)"
            name="precoCusto"
            inputMode="decimal"
            defaultValue={valor("precoCusto", "")}
            erro={erros.precoCusto}
            ajuda="Opcional. Depois, só muda pelo custo médio das compras."
          />
        )}
        <Campo rotulo="Preço de venda (R$)" name="precoVenda" required inputMode="decimal" defaultValue={valor("precoVenda", decimal(produto?.precoVenda))} erro={erros.precoVenda} />
        <div className="sm:col-span-2">
          <label htmlFor="campo-descricao-produto" className="mb-1 block text-sm font-medium">Descrição</label>
          <textarea id="campo-descricao-produto" name="descricao" rows={3} maxLength={2000} className="campo" defaultValue={valor("descricao", produto?.descricao ?? "")} />
        </div>
        {!produto && (
          <p className="text-sm text-muted sm:col-span-2">
            O produto começa com estoque zero. A entrada acontece ao receber uma compra (ou por ajuste do administrador).
          </p>
        )}
      </fieldset>

      <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        <Link href={produto ? `/produtos/${produto.id}` : "/produtos"} className="botao-secundario">Cancelar</Link>
        <button type="submit" className="botao-primario" disabled={pending}>
          {pending ? "Salvando…" : produto ? "Salvar alterações" : "Cadastrar produto"}
        </button>
      </div>
    </form>
  );
}
