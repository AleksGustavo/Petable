import { apiUrl, requestIdAtual } from "@/lib/api";
import { getSessao } from "@/lib/session";

/** Download do JSON de dados pessoais (LGPD), repassando o token do cookie. A API exige ADMIN e audita. */
export async function GET(_req: Request, ctx: RouteContext<"/api/clientes/[id]/dados-pessoais">) {
  const sessao = await getSessao();
  if (!sessao) return new Response(null, { status: 401 });
  const { id } = await ctx.params;
  if (!/^\d+$/.test(id)) return new Response(null, { status: 404 });
  if (sessao.papel !== "ADMIN") return new Response(null, { status: 403 });
  const requestId = await requestIdAtual();
  const res = await fetch(apiUrl(`/api/v1/clientes/${id}/dados-pessoais`), {
    headers: { Authorization: `Bearer ${sessao.token}`, ...(requestId && { "X-Request-Id": requestId }) },
    cache: "no-store",
  });
  if (!res.ok) return new Response(null, { status: res.status });
  return new Response(res.body, {
    status: 200,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Content-Disposition": res.headers.get("Content-Disposition") ?? `attachment; filename="dados-cliente-${id}.json"`,
      "Cache-Control": "no-store",
    },
  });
}
