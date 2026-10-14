import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { Aviso } from "@/components/aviso";
import { LotesAlertas } from "@/components/lotes-alertas";
import { LotesLista } from "@/components/lotes-lista";
import { apiFetch } from "@/lib/api";
import type { Pagina } from "@/lib/clientes";
import { hojeISO } from "@/lib/datas";
import { type AlertasEstoque, type ConfiguracaoLotes, type Lote } from "@/lib/lotes";
import { podeVerLotes } from "@/lib/permissoes";
import { exigirSessao } from "@/lib/session";

export const metadata: Metadata = { title: "Lotes e validade" };

/** Filtro "situação" da lista: vencidos, bloqueados, ou janela de vencimento em dias (30/60/90…). */
function filtrosDaApi(situacao: string): Record<string, string> {
  if (situacao === "vencidos") return { vencido: "true", comSaldo: "true" };
  if (situacao === "bloqueados") return { status: "BLOQUEADO" };
  if (/^\d{1,4}$/.test(situacao)) return { venceEmDias: situacao };
  return {};
}

export default async function LotesPage(props: PageProps<"/estoque/lotes">) {
  const sessao = await exigirSessao();
  if (!podeVerLotes(sessao.papel)) redirect("/dashboard");
  const sp = await props.searchParams;
  const texto = (k: string) => (typeof sp[k] === "string" ? (sp[k] as string) : "");

  const config = await apiFetch<ConfiguracaoLotes>("/api/v1/estoque/configuracao");
  if (!config.lotesHabilitado) {
    return (
      <div className="space-y-4">
        <h1 className="text-2xl font-semibold">Lotes e validade</h1>
        <Aviso>O controle de lote e validade ainda não foi liberado nesta instalação.</Aviso>
      </div>
    );
  }

  const busca = texto("busca").trim();
  const situacao = texto("situacao");
  const comSaldo = texto("semSaldo") === "1" ? "false" : "true";
  const pagina = Math.max(0, Number(texto("page")) || 0);
  const consulta = new URLSearchParams({ comSaldo, page: String(pagina), size: "20", ...(busca && { busca }), ...filtrosDaApi(situacao) });
  const [alertas, lotes] = await Promise.all([
    apiFetch<AlertasEstoque>("/api/v1/estoque/alertas"),
    apiFetch<Pagina<Lote>>(`/api/v1/lotes?${consulta}`),
  ]);
  const link = (p: number) => `/estoque/lotes?${new URLSearchParams({ ...(busca && { busca }), ...(situacao && { situacao }), ...(texto("semSaldo") && { semSaldo: "1" }), page: String(p) })}`;

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl font-semibold">Lotes e validade</h1>
        <p className="text-sm text-muted">Consulte o estoque por lote, acompanhe os vencimentos e rastreie entradas e saídas.</p>
      </header>

      <LotesAlertas alertas={alertas} />

      <form className="cartao grid gap-3 p-4 sm:grid-cols-[1fr_14rem_auto_auto] sm:items-end" role="search" aria-label="Filtrar lotes">
        <div>
          <label htmlFor="filtro-busca" className="mb-1 block text-sm font-medium">Produto, código de barras ou código do lote</label>
          <input id="filtro-busca" name="busca" defaultValue={busca} className="campo" maxLength={100} />
        </div>
        <div>
          <label htmlFor="filtro-situacao" className="mb-1 block text-sm font-medium">Vencimento</label>
          <select id="filtro-situacao" name="situacao" defaultValue={situacao} className="campo">
            <option value="">Todos</option>
            <option value="vencidos">Já vencidos</option>
            {config.faixasAlertaDias.map((d) => (
              <option key={d} value={String(d)}>Vencem em até {d} dias</option>
            ))}
            <option value="bloqueados">Bloqueados</option>
          </select>
        </div>
        <label className="flex items-center gap-2 pb-2 text-sm">
          <input type="checkbox" name="semSaldo" value="1" defaultChecked={texto("semSaldo") === "1"} />
          Incluir sem saldo
        </label>
        <button type="submit" className="botao-primario">Filtrar</button>
      </form>

      <p className="text-sm text-muted" aria-live="polite">{lotes.totalElements} lote(s) encontrado(s).</p>
      <LotesLista lotes={lotes.content} hoje={hojeISO()} />
      {lotes.totalPages > 1 && (
        <nav aria-label="Paginação dos lotes" className="flex items-center justify-between text-sm">
          {pagina > 0 ? <Link href={link(pagina - 1)} className="botao-secundario">← Anterior</Link> : <span />}
          <span className="text-muted">Página {pagina + 1} de {lotes.totalPages}</span>
          {pagina + 1 < lotes.totalPages ? <Link href={link(pagina + 1)} className="botao-secundario">Próxima →</Link> : <span />}
        </nav>
      )}
    </div>
  );
}
