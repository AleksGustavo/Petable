import type { Metadata } from "next";
import Link from "next/link";
import { ProdutosFiltros, ProdutosLista } from "@/components/produtos-lista";
import { apiFetch } from "@/lib/api";
import type { Pagina } from "@/lib/clientes";
import type { CategoriaProduto, Produto } from "@/lib/estoque";
import { podeEditarProdutos } from "@/lib/permissoes";
import { exigirSessao } from "@/lib/session";

export const metadata: Metadata = { title: "Produtos" };

export default async function ProdutosPage(props: PageProps<"/produtos">) {
  const sp = await props.searchParams;
  const texto = (k: string) => (typeof sp[k] === "string" ? (sp[k] as string) : "");
  const [sessao, categorias] = await Promise.all([exigirSessao(), apiFetch<CategoriaProduto[]>("/api/v1/categorias-produto")]);

  const q = texto("q").trim().slice(0, 120);
  const categoriaId = categorias.some((c) => String(c.id) === texto("categoriaId")) ? texto("categoriaId") : "";
  const ativo = ["true", "false"].includes(texto("ativo")) ? texto("ativo") : "";
  const abaixoMinimo = texto("abaixoMinimo") === "true";
  const pagina = Math.max(0, Number(texto("page")) || 0);
  const filtros = { ...(q && { q }), ...(categoriaId && { categoriaId }), ...(ativo && { ativo }), ...(abaixoMinimo && { abaixoMinimo: "true" }) };
  const dados = await apiFetch<Pagina<Produto>>(`/api/v1/produtos?${new URLSearchParams({ ...filtros, page: String(pagina), size: "20" })}`);
  const link = (p: number) => `/produtos?${new URLSearchParams({ ...filtros, page: String(p) })}`;

  return (
    <div className="space-y-6">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Produtos</h1>
          <p className="text-sm text-muted">{dados.totalElements} encontrado(s)</p>
        </div>
        {podeEditarProdutos(sessao.papel) && <Link href="/produtos/novo" className="botao-primario">+ Novo produto</Link>}
      </header>

      <ProdutosFiltros filtros={{ q, categoriaId, ativo, abaixoMinimo }} categorias={categorias} />
      <ProdutosLista produtos={dados.content} filtrado={Object.keys(filtros).length > 0} />

      {dados.totalPages > 1 && (
        <nav aria-label="Paginação" className="flex items-center justify-between text-sm">
          {pagina > 0 ? <Link href={link(pagina - 1)} className="botao-secundario">← Anterior</Link> : <span />}
          <span className="text-muted">Página {pagina + 1} de {dados.totalPages}</span>
          {pagina + 1 < dados.totalPages ? <Link href={link(pagina + 1)} className="botao-secundario">Próxima →</Link> : <span />}
        </nav>
      )}
    </div>
  );
}
