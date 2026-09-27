import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { FornecedoresLista } from "@/components/fornecedores-lista";
import { apiFetch } from "@/lib/api";
import type { Pagina } from "@/lib/clientes";
import type { Fornecedor } from "@/lib/estoque";
import { podeVerFornecedoresECompras } from "@/lib/permissoes";
import { exigirSessao } from "@/lib/session";

export const metadata: Metadata = { title: "Fornecedores" };

export default async function FornecedoresPage(props: PageProps<"/fornecedores">) {
  const sessao = await exigirSessao();
  if (!podeVerFornecedoresECompras(sessao.papel)) redirect("/dashboard");
  const sp = await props.searchParams;
  const texto = (k: string) => (typeof sp[k] === "string" ? (sp[k] as string) : "");
  const q = texto("q").trim().slice(0, 100);
  const ativo = ["true", "false"].includes(texto("ativo")) ? texto("ativo") : "";
  const pagina = Math.max(0, Number(texto("page")) || 0);
  const filtros = { ...(q && { q }), ...(ativo && { ativo }) };
  const dados = await apiFetch<Pagina<Fornecedor>>(`/api/v1/fornecedores?${new URLSearchParams({ ...filtros, page: String(pagina), size: "20" })}`);
  const link = (p: number) => `/fornecedores?${new URLSearchParams({ ...filtros, page: String(p) })}`;

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl font-semibold">Fornecedores</h1>
        <p className="text-sm text-muted">{dados.totalElements} encontrado(s)</p>
      </header>

      <form role="search" className="grid gap-2 sm:grid-cols-[1fr_11rem_auto]">
        <label htmlFor="busca-fornecedor" className="sr-only">Buscar por nome, razão social ou CNPJ</label>
        <input id="busca-fornecedor" name="q" defaultValue={q} maxLength={100} placeholder="Nome, razão social ou CNPJ" className="campo" />
        <label htmlFor="filtro-ativo-fornecedor" className="sr-only">Situação</label>
        <select id="filtro-ativo-fornecedor" name="ativo" defaultValue={ativo} className="campo">
          <option value="">Todas as situações</option>
          <option value="true">Ativos</option>
          <option value="false">Inativos</option>
        </select>
        <button type="submit" className="botao-secundario">Filtrar</button>
      </form>

      <FornecedoresLista fornecedores={dados.content} papel={sessao.papel} filtrado={Object.keys(filtros).length > 0} />

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
