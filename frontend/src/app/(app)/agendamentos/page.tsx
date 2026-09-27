import type { Metadata } from "next";
import Link from "next/link";
import { unstable_rethrow } from "next/navigation";
import { AgendaLista } from "@/components/agenda-lista";
import { Aviso } from "@/components/aviso";
import { STATUS, type Agendamento, type Servico, type StatusAgendamento } from "@/lib/agendamentos";
import { apiFetch } from "@/lib/api";
import type { Pagina } from "@/lib/clientes";
import { hojeISO } from "@/lib/datas";
import { formatarData } from "@/lib/format";
import { podeAdministrarServicos, podeAgendar } from "@/lib/permissoes";
import { exigirSessao } from "@/lib/session";

export const metadata: Metadata = { title: "Agendamentos" };

const somarDias = (iso: string, dias: number) => {
  const [a, m, d] = iso.split("-").map(Number);
  return new Date(Date.UTC(a, m - 1, d + dias)).toISOString().slice(0, 10);
};

export default async function AgendamentosPage(props: PageProps<"/agendamentos">) {
  const sp = await props.searchParams;
  const texto = (k: string) => (typeof sp[k] === "string" ? (sp[k] as string) : "");
  const data = /^\d{4}-\d{2}-\d{2}$/.test(texto("data")) ? texto("data") : hojeISO();
  const modo = texto("modo") === "semana" ? "semana" : "dia";
  const status = texto("status") in STATUS ? (texto("status") as StatusAgendamento) : "";
  const q = texto("q").slice(0, 100);
  const servicoId = /^\d+$/.test(texto("servicoId")) ? texto("servicoId") : "";
  const fim = modo === "semana" ? somarDias(data, 6) : data;
  const passo = modo === "semana" ? 7 : 1;

  const filtros = { ...(status && { status }), ...(q && { q }), ...(servicoId && { servicoId }) };
  const link = (params: Record<string, string>) =>
    `/agendamentos?${new URLSearchParams({ data, modo, ...filtros, ...params })}`;

  const sessao = await exigirSessao();
  const qs = new URLSearchParams({ inicio: data, fim, size: "100", ...filtros });
  let agenda: Pagina<Agendamento> | null = null;
  let servicos: Servico[] = [];
  try {
    [agenda, servicos] = await Promise.all([
      apiFetch<Pagina<Agendamento>>(`/api/v1/agendamentos?${qs}`),
      apiFetch<Servico[]>("/api/v1/servicos?ativo=true"),
    ]);
  } catch (e) {
    unstable_rethrow(e);
  }

  return (
    <div className="space-y-6">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Agendamentos</h1>
          <p className="text-sm text-muted">
            {modo === "semana" ? `${formatarData(data)} a ${formatarData(fim)}` : formatarData(data)}
          </p>
        </div>
        <div className="flex flex-col gap-2 sm:flex-row">
          {podeAdministrarServicos(sessao.papel) && <Link href="/servicos" className="botao-secundario">Gerenciar serviços</Link>}
          {podeAgendar(sessao.papel) && <Link href="/agendamentos/novo" className="botao-primario">+ Novo agendamento</Link>}
        </div>
      </header>

      {sp.ok && <Aviso>Agendamento salvo.</Aviso>}

      <nav aria-label="Navegação da agenda" className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-1">
          <Link href={link({ data: somarDias(data, -passo) })} className="botao-secundario px-3" aria-label={modo === "semana" ? "Semana anterior" : "Dia anterior"}>
            ‹ <span className="hidden sm:inline">{modo === "semana" ? "Semana anterior" : "Dia anterior"}</span>
          </Link>
          <Link href={link({ data: hojeISO() })} className="botao-secundario px-3">Hoje</Link>
          <Link href={link({ data: somarDias(data, passo) })} className="botao-secundario px-3" aria-label={modo === "semana" ? "Próxima semana" : "Próximo dia"}>
            <span className="hidden sm:inline">{modo === "semana" ? "Próxima semana" : "Próximo dia"}</span> ›
          </Link>
        </div>
        <div className="flex rounded-lg border border-border bg-surface p-1" role="group" aria-label="Período">
          {(["dia", "semana"] as const).map((m) => (
            <Link
              key={m}
              href={link({ modo: m })}
              aria-current={modo === m ? "page" : undefined}
              className={`rounded-md px-3 py-1.5 text-sm font-medium ${modo === m ? "bg-brand text-white" : "text-muted hover:bg-brand-soft"}`}
            >
              {m === "dia" ? "Dia" : "Semana"}
            </Link>
          ))}
        </div>
      </nav>

      <form role="search" className="grid gap-2 sm:grid-cols-[1fr_12rem_12rem_auto]">
        <input type="hidden" name="data" value={data} />
        <input type="hidden" name="modo" value={modo} />
        <label htmlFor="busca-agenda" className="sr-only">Buscar por pet ou tutor</label>
        <input id="busca-agenda" name="q" defaultValue={q} maxLength={100} placeholder="Buscar por pet ou tutor" className="campo" />
        <label htmlFor="filtro-status" className="sr-only">Status</label>
        <select id="filtro-status" name="status" defaultValue={status} className="campo">
          <option value="">Todos os status</option>
          {Object.entries(STATUS).map(([valor, s]) => (
            <option key={valor} value={valor}>{s.rotulo}</option>
          ))}
        </select>
        <label htmlFor="filtro-servico" className="sr-only">Serviço</label>
        <select id="filtro-servico" name="servicoId" defaultValue={servicoId} className="campo">
          <option value="">Todos os serviços</option>
          {servicos.map((s) => (
            <option key={s.id} value={s.id}>{s.nome}</option>
          ))}
        </select>
        <button type="submit" className="botao-secundario">Filtrar</button>
      </form>

      {agenda === null ? (
        <Aviso tipo="erro">Não foi possível carregar os agendamentos. Tente novamente.</Aviso>
      ) : (
        <>
          <AgendaLista agendamentos={agenda.content} agruparPorDia={modo === "semana"} filtrado={Object.keys(filtros).length > 0} />
          {agenda.totalElements > agenda.content.length && (
            <p className="text-sm text-muted">Mostrando {agenda.content.length} de {agenda.totalElements}. Use os filtros para refinar.</p>
          )}
        </>
      )}
    </div>
  );
}
