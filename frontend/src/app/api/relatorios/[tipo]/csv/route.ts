import { apiUrl } from "@/lib/api";
import { definicao, erroPeriodo, podeVerRelatorio } from "@/lib/relatorios";
import { getSessao } from "@/lib/session";

/**
 * Repassa o CSV da API (BOM + ';') ao navegador sem expor o token. A API é quem
 * autoriza e audita; aqui só barramos antes o que já sabemos que será negado.
 */
export async function GET(req: Request, ctx: RouteContext<"/api/relatorios/[tipo]/csv">) {
  const sessao = await getSessao();
  if (!sessao) return new Response(null, { status: 401 });
  const { tipo } = await ctx.params;
  if (!definicao(tipo)) return new Response(null, { status: 404 });
  if (!podeVerRelatorio(sessao.papel, tipo)) return new Response(null, { status: 403 });
  const sp = new URL(req.url).searchParams;
  const dataInicio = sp.get("dataInicio") ?? "";
  const dataFim = sp.get("dataFim") ?? "";
  if (erroPeriodo(dataInicio, dataFim)) return new Response(null, { status: 400 });

  const res = await fetch(apiUrl(`/api/v1/relatorios/${tipo}/csv?${new URLSearchParams({ dataInicio, dataFim })}`), {
    headers: { Authorization: `Bearer ${sessao.token}` },
    cache: "no-store",
  });
  if (!res.ok) return new Response(null, { status: res.status });
  const headers = new Headers({ "Content-Type": res.headers.get("Content-Type") ?? "text/csv; charset=UTF-8", "Cache-Control": "no-store" });
  const disposicao = res.headers.get("Content-Disposition");
  if (disposicao) headers.set("Content-Disposition", disposicao);
  return new Response(res.body, { status: 200, headers });
}
