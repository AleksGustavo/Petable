import { apiFetch } from "@/lib/api";
import type { Pagina } from "@/lib/clientes";
import type { Pet } from "@/lib/pets";

/** Pets ativos de um tutor, para o passo Tutor → Pet do novo agendamento. */
export async function GET(req: Request) {
  const clienteId = new URL(req.url).searchParams.get("clienteId") ?? "";
  if (!/^\d+$/.test(clienteId)) return Response.json([]);
  const pagina = await apiFetch<Pagina<Pet>>(`/api/v1/pets?clienteId=${clienteId}&size=100`);
  return Response.json(pagina.content.map(({ id, nome, porte, especie }) => ({ id, nome, porte, especie })));
}
