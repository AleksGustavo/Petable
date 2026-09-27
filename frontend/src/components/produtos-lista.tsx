import Link from "next/link";
import { BadgeAtivo, BadgeEstoque } from "@/components/estoque-badges";
import { moeda } from "@/lib/agendamentos";
import type { CategoriaProduto, Produto } from "@/lib/estoque";

export type FiltrosProdutos = { q: string; categoriaId: string; ativo: string; abaixoMinimo: boolean };

export function ProdutosFiltros({ filtros, categorias }: { filtros: FiltrosProdutos; categorias: CategoriaProduto[] }) {
  return (
    <form role="search" className="grid gap-2 sm:grid-cols-2 lg:grid-cols-[1fr_11rem_10rem_auto_auto] lg:items-center">
      <label htmlFor="busca-produto" className="sr-only">Buscar por nome ou código de barras</label>
      <input id="busca-produto" name="q" defaultValue={filtros.q} maxLength={120} placeholder="Nome ou código de barras" className="campo" />
      <label htmlFor="filtro-categoria" className="sr-only">Categoria</label>
      <select id="filtro-categoria" name="categoriaId" defaultValue={filtros.categoriaId} className="campo">
        <option value="">Todas as categorias</option>
        {categorias.map((c) => (
          <option key={c.id} value={c.id}>{c.nome}</option>
        ))}
      </select>
      <label htmlFor="filtro-ativo-produto" className="sr-only">Situação</label>
      <select id="filtro-ativo-produto" name="ativo" defaultValue={filtros.ativo} className="campo">
        <option value="">Todas as situações</option>
        <option value="true">Ativos</option>
        <option value="false">Inativos</option>
      </select>
      <label className="flex items-center gap-2 text-sm">
        <input type="checkbox" name="abaixoMinimo" value="true" defaultChecked={filtros.abaixoMinimo} className="size-4 accent-[var(--brand)]" />
        Abaixo do mínimo
      </label>
      <button type="submit" className="botao-secundario">Filtrar</button>
    </form>
  );
}

export function ProdutosLista({ produtos, filtrado = false }: { produtos: Produto[]; filtrado?: boolean }) {
  if (produtos.length === 0) {
    return (
      <p className="cartao p-10 text-center text-sm text-muted">
        {filtrado ? "Nenhum produto encontrado com os filtros selecionados." : "Nenhum produto cadastrado."}
      </p>
    );
  }
  return (
    <>
      <ul className="space-y-3 md:hidden">
        {produtos.map((p) => (
          <li key={p.id}>
            <Link href={`/produtos/${p.id}`} className={`cartao block space-y-2 p-4 transition hover:border-brand ${p.ativo ? "" : "opacity-70"}`}>
              <div className="flex items-start justify-between gap-2">
                <p className="font-medium">{p.nome}</p>
                <p className="whitespace-nowrap font-semibold tabular-nums">{moeda(p.precoVenda)}</p>
              </div>
              <p className="text-sm text-muted">
                {p.categoria.nome}
                {p.codigoBarras && ` · ${p.codigoBarras}`}
              </p>
              <div className="flex flex-wrap items-center gap-2 text-sm">
                <span className="tabular-nums">
                  Saldo <strong>{p.quantidadeEstoque}</strong> / mín. {p.estoqueMinimo}
                </span>
                <BadgeEstoque abaixoDoMinimo={p.abaixoDoMinimo} />
                {!p.ativo && <BadgeAtivo ativo={false} />}
              </div>
            </Link>
          </li>
        ))}
      </ul>
      <div className="cartao hidden overflow-x-auto md:block">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-border text-xs uppercase tracking-wide text-muted">
            <tr>
              <th scope="col" className="px-4 py-3 font-medium">Produto</th>
              <th scope="col" className="px-4 py-3 font-medium">Categoria</th>
              <th scope="col" className="px-4 py-3 text-right font-medium">Venda</th>
              <th scope="col" className="px-4 py-3 text-right font-medium">Saldo</th>
              <th scope="col" className="px-4 py-3 font-medium">Estoque</th>
              <th scope="col" className="px-4 py-3 font-medium">Situação</th>
              <th scope="col" className="px-4 py-3"><span className="sr-only">Ações</span></th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {produtos.map((p) => (
              <tr key={p.id} className={`hover:bg-brand-soft/40 ${p.ativo ? "" : "text-muted"}`}>
                <td className="px-4 py-3">
                  <p className="font-medium">{p.nome}</p>
                  {p.codigoBarras && <p className="text-xs text-muted tabular-nums">{p.codigoBarras}</p>}
                </td>
                <td className="px-4 py-3">{p.categoria.nome}</td>
                <td className="px-4 py-3 text-right tabular-nums">{moeda(p.precoVenda)}</td>
                <td className="px-4 py-3 text-right tabular-nums">
                  {p.quantidadeEstoque} <span className="text-xs text-muted">/ {p.estoqueMinimo}</span>
                </td>
                <td className="px-4 py-3"><BadgeEstoque abaixoDoMinimo={p.abaixoDoMinimo} /></td>
                <td className="px-4 py-3"><BadgeAtivo ativo={p.ativo} /></td>
                <td className="px-4 py-3 text-right">
                  <Link href={`/produtos/${p.id}`} className="font-medium text-brand hover:underline">Abrir</Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
