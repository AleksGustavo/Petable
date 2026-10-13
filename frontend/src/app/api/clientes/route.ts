import { apiFetch } from "@/lib/api";
import type { Cliente, Pagina } from "@/lib/clientes";

/** Busca rápida de tutor para o seletor do cadastro de pet. */
export async function GET(req: Request) {
  const busca = new URL(req.url).searchParams.get("busca")?.trim() ?? "";
  if (busca.length < 2) return Response.json([]);
  const qs = new URLSearchParams({ busca: busca.slice(0, 100), size: "8" });
  const pagina = await apiFetch<Pagina<Cliente>>(`/api/v1/clientes?${qs}`);
  return Response.json(pagina.content.map(({ id, nome, telefone }) => ({ id, nome, telefone })));
}
