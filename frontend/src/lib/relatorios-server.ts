import "server-only";
import { apiFetch } from "@/lib/api";
import { erroPeriodo, periodoPadrao, type DadosRelatorio, type TipoRelatorio } from "@/lib/relatorios";

const DATA = /^\d{4}-\d{2}-\d{2}$/;

/** Lê dataInicio/dataFim da URL (padrão: mês corrente) e valida com as mesmas regras da API. */
export function lerPeriodo(sp: Record<string, string | string[] | undefined>, hoje: string) {
  const texto = (k: string) => (typeof sp[k] === "string" ? (sp[k] as string) : "");
  const padrao = periodoPadrao(hoje);
  const dataInicio = DATA.test(texto("dataInicio")) ? texto("dataInicio") : padrao.dataInicio;
  const dataFim = DATA.test(texto("dataFim")) ? texto("dataFim") : padrao.dataFim;
  return { dataInicio, dataFim, erro: erroPeriodo(dataInicio, dataFim) };
}

export async function carregarRelatorio(tipo: TipoRelatorio, dataInicio: string, dataFim: string): Promise<DadosRelatorio> {
  const qs = new URLSearchParams({ dataInicio, dataFim });
  const dados = await apiFetch<unknown>(`/api/v1/relatorios/${tipo}?${qs}`);
  return { tipo, dados } as DadosRelatorio;
}
