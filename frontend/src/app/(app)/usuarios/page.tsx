import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { UsuariosLista } from "@/components/usuarios-lista";
import { apiFetch } from "@/lib/api";
import type { Pagina } from "@/lib/clientes";
import { podeAdministrarUsuarios } from "@/lib/permissoes";
import { exigirSessao } from "@/lib/session";
import { PAPEIS, type Usuario } from "@/lib/usuarios";

export const metadata: Metadata = { title: "Usuários" };

export default async function UsuariosPage(props: PageProps<"/usuarios">) {
  const sessao = await exigirSessao();
  if (!podeAdministrarUsuarios(sessao.papel)) redirect("/dashboard");
  const sp = await props.searchParams;
  const texto = (k: string) => (typeof sp[k] === "string" ? (sp[k] as string) : "");
  const q = texto("q").slice(0, 100);
  const papel = PAPEIS.some((p) => p.valor === texto("papel")) ? texto("papel") : "";
  const ativo = ["true", "false"].includes(texto("ativo")) ? texto("ativo") : "";
  const pagina = Math.max(0, Number(texto("page")) || 0);
  const filtros = { ...(q && { q }), ...(papel && { papel }), ...(ativo && { ativo }) };
  const dados = await apiFetch<Pagina<Usuario>>(`/api/v1/usuarios?${new URLSearchParams({ ...filtros, page: String(pagina), size: "20" })}`);
  const link = (p: number) => `/usuarios?${new URLSearchParams({ ...filtros, page: String(p) })}`;

  return (
    <div className="space-y-6">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Usuários</h1>
          <p className="text-sm text-muted">{dados.totalElements} encontrado(s)</p>
        </div>
        <Link href="/usuarios/novo" className="botao-primario">+ Novo usuário</Link>
      </header>

      <form role="search" className="grid gap-2 sm:grid-cols-[1fr_11rem_10rem_auto]">
        <label htmlFor="busca-usuario" className="sr-only">Buscar por usuário, e-mail ou funcionário</label>
        <input id="busca-usuario" name="q" defaultValue={q} maxLength={100} placeholder="Usuário, e-mail ou funcionário" className="campo" />
        <label htmlFor="filtro-papel" className="sr-only">Papel</label>
        <select id="filtro-papel" name="papel" defaultValue={papel} className="campo">
          <option value="">Todos os papéis</option>
          {PAPEIS.map((p) => (
            <option key={p.valor} value={p.valor}>{p.rotulo}</option>
          ))}
        </select>
        <label htmlFor="filtro-ativo" className="sr-only">Situação</label>
        <select id="filtro-ativo" name="ativo" defaultValue={ativo} className="campo">
          <option value="">Todas as situações</option>
          <option value="true">Ativos</option>
          <option value="false">Inativos</option>
        </select>
        <button type="submit" className="botao-secundario">Filtrar</button>
      </form>

      <UsuariosLista usuarios={dados.content} filtrado={Object.keys(filtros).length > 0} />

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
