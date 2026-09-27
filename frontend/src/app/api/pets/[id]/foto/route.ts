import { apiUrl } from "@/lib/api";
import { getSessao } from "@/lib/session";

/**
 * Serve a foto do pet para o <img>: o navegador não tem o JWT, então o
 * servidor do Next busca na API com o token do cookie e repassa os bytes.
 */
export async function GET(_req: Request, ctx: RouteContext<"/api/pets/[id]/foto">) {
  const { id } = await ctx.params;
  if (!/^\d+$/.test(id)) return new Response(null, { status: 404 });
  const sessao = await getSessao();
  if (!sessao) return new Response(null, { status: 401 });

  const res = await fetch(apiUrl(`/api/v1/pets/${id}/foto`), {
    headers: { Authorization: `Bearer ${sessao.token}` },
    cache: "no-store",
  });
  if (!res.ok) return new Response(null, { status: res.status });
  return new Response(res.body, {
    headers: {
      "Content-Type": res.headers.get("Content-Type") ?? "application/octet-stream",
      "Cache-Control": "private, max-age=60",
      "X-Content-Type-Options": "nosniff",
    },
  });
}
