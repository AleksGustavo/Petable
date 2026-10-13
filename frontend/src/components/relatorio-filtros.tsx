import Link from "next/link";
import { atalhosPeriodo, type DefinicaoRelatorio, type TipoRelatorio } from "@/lib/relatorios";

const qs = (dataInicio: string, dataFim: string) => new URLSearchParams({ dataInicio, dataFim }).toString();

/** Período (GET, sem JS), atalhos, exportar CSV e imprimir. */
export function RelatorioFiltros({
  tipo,
  dataInicio,
  dataFim,
  hoje,
  erro,
}: {
  tipo: TipoRelatorio;
  dataInicio: string;
  dataFim: string;
  hoje: string;
  erro?: string | null;
}) {
  const atalhos = atalhosPeriodo(hoje);
  return (
    <div className="space-y-3 print:hidden">
      <form role="search" className="grid gap-2 sm:grid-cols-[1fr_1fr_auto] sm:items-end">
        <div>
          <label htmlFor="rel-inicio" className="mb-1 block text-xs text-muted">De</label>
          <input id="rel-inicio" type="date" name="dataInicio" defaultValue={dataInicio} max={hoje} className="campo" required />
        </div>
        <div>
          <label htmlFor="rel-fim" className="mb-1 block text-xs text-muted">Até</label>
          <input id="rel-fim" type="date" name="dataFim" defaultValue={dataFim} className="campo" required />
        </div>
        <button type="submit" className="botao-secundario">Aplicar</button>
      </form>
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-xs text-muted">Atalhos:</span>
        {atalhos.map((a) => {
          const ativo = a.dataInicio === dataInicio && a.dataFim === dataFim;
          return (
            <Link
              key={a.rotulo}
              href={`/relatorios/${tipo}?${qs(a.dataInicio, a.dataFim)}`}
              aria-current={ativo ? "true" : undefined}
              className={`rounded-full border px-3 py-1 text-xs font-medium ${ativo ? "border-brand bg-brand text-white" : "border-border hover:border-brand"}`}
            >
              {a.rotulo}
            </Link>
          );
        })}
      </div>
      {erro ? (
        <p role="alert" className="text-sm text-danger">{erro}</p>
      ) : (
        <div className="flex flex-col gap-2 sm:flex-row">
          <a href={`/api/relatorios/${tipo}/csv?${qs(dataInicio, dataFim)}`} className="botao-secundario" download>
            Exportar CSV
          </a>
          <Link href={`/relatorios/${tipo}/imprimir?${qs(dataInicio, dataFim)}`} target="_blank" className="botao-secundario">
            Imprimir
          </Link>
        </div>
      )}
    </div>
  );
}

/** Hub: um card por relatório permitido ao papel (VENDAS não vê custo, margem nem financeiro). */
export function RelatoriosHub({ relatorios, hoje }: { relatorios: DefinicaoRelatorio[]; hoje: string }) {
  if (relatorios.length === 0) return <p className="cartao p-10 text-center text-sm text-muted">Nenhum relatório disponível para o seu perfil.</p>;
  const periodo = qs(`${hoje.slice(0, 8)}01`, hoje);
  return (
    <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
      {relatorios.map((r) => (
        <li key={r.tipo}>
          <Link href={`/relatorios/${r.tipo}?${periodo}`} className="cartao group block h-full p-5 transition hover:border-brand">
            <p className="font-semibold">{r.titulo}</p>
            <p className="mt-1 text-sm text-muted">{r.descricao}</p>
            <p className="mt-3 text-sm font-medium text-brand group-hover:underline">Abrir →</p>
          </Link>
        </li>
      ))}
    </ul>
  );
}

