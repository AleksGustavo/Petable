import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { ComprasLista } from "@/components/compras-lista";
import { Aviso } from "@/components/aviso";
import { apiFetch } from "@/lib/api";
import type { Pagina } from "@/lib/clientes";
import { STATUS_COMPRA, type Compra, type FornecedorOpcao } from "@/lib/estoque";
import { podeAdministrarCompras, podeVerFornecedoresECompras } from "@/lib/permissoes";
import { exigirSessao } from "@/lib/session";

export const metadata: Metadata = { title: "Compras" };

const DATA = /^\d{4}-\d{2}-\d{2}$/;

export default async function ComprasPage(props: PageProps<"/compras">) {
  const sessao = await exigirSessao();
  if (!podeVerFornecedoresECompras(sessao.papel)) redirect("/dashboard");
  const sp = await props.searchParams;
  const texto = (k: string) => (typeof sp[k] === "string" ? (sp[k] as string) : "");
  const status = STATUS_COMPRA.some((s) => s.valor === texto("status")) ? texto("status") : "";
  const fornecedorId = /^\d+$/.test(texto("fornecedorId")) ? texto("fornecedorId") : "";
  const dataInicio = DATA.test(texto("dataInicio")) ? texto("dataInicio") : "";
  const dataFim = DATA.test(texto("dataFim")) ? texto("dataFim") : "";
  const periodoInvalido = Boolean(dataInicio && dataFim && dataFim < dataInicio);
  const pagina = Math.max(0, Number(texto("page")) || 0);
  const filtros = {
    ...(status && { status }),
    ...(fornecedorId && { fornecedorId }),
    ...(!periodoInvalido && dataInicio && { dataInicio }),
    ...(!periodoInvalido && dataFim && { dataFim }),
  };
  const [fornecedores, dados] = await Promise.all([
    apiFetch<FornecedorOpcao[]>("/api/v1/fornecedores/opcoes"),
    apiFetch<Pagina<Compra>>(`/api/v1/compras?${new URLSearchParams({ ...filtros, page: String(pagina), size: "20" })}`),
  ]);
  const link = (p: number) => `/compras?${new URLSearchParams({ ...filtros, page: String(p) })}`;

  return (
    <div className="space-y-6">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Compras</h1>
          <p className="text-sm text-muted">{dados.totalElements} encontrada(s)</p>
        </div>
        {podeAdministrarCompras(sessao.papel) && <Link href="/compras/nova" className="botao-primario">+ Nova compra</Link>}
      </header>

      <form role="search" className="grid gap-2 sm:grid-cols-2 lg:grid-cols-[10rem_1fr_10rem_10rem_auto] lg:items-end">
        <div>
          <label htmlFor="filtro-status" className="mb-1 block text-xs text-muted">Status</label>
          <select id="filtro-status" name="status" defaultValue={status} className="campo">
            <option value="">Todos</option>
            {STATUS_COMPRA.map((s) => (
              <option key={s.valor} value={s.valor}>{s.rotulo}</option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="filtro-fornecedor" className="mb-1 block text-xs text-muted">Fornecedor</label>
          <select id="filtro-fornecedor" name="fornecedorId" defaultValue={fornecedorId} className="campo">
            <option value="">Todos</option>
            {fornecedores.map((f) => (
              <option key={f.id} value={f.id}>{f.nomeFantasia}</option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="filtro-inicio" className="mb-1 block text-xs text-muted">De</label>
          <input id="filtro-inicio" type="date" name="dataInicio" defaultValue={dataInicio} className="campo" />
        </div>
        <div>
          <label htmlFor="filtro-fim" className="mb-1 block text-xs text-muted">Até</label>
          <input id="filtro-fim" type="date" name="dataFim" defaultValue={dataFim} className="campo" />
        </div>
        <button type="submit" className="botao-secundario">Filtrar</button>
      </form>

      {periodoInvalido && <Aviso tipo="erro">A data final não pode ser anterior à inicial. O filtro de período foi ignorado.</Aviso>}

      <ComprasLista compras={dados.content} filtrado={Object.keys(filtros).length > 0} />

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
