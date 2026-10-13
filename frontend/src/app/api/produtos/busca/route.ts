import { apiFetch } from "@/lib/api";
import type { ProdutoRapido } from "@/lib/estoque";

/** Busca rápida (nome ou código de barras) para os itens da compra. Só produtos ativos. */
export async function GET(req: Request) {
  const q = new URL(req.url).searchParams.get("q")?.trim() ?? "";
  if (q.length < 2) return Response.json([]);
  const qs = new URLSearchParams({ q: q.slice(0, 120) });
  const produtos = await apiFetch<ProdutoRapido[]>(`/api/v1/produtos/busca-rapida?${qs}`);
  return Response.json(produtos.slice(0, 10));
}
