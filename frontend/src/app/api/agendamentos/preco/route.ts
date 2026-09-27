import { ApiError, apiFetch } from "@/lib/api";
import type { PrecoPrevisto } from "@/lib/agendamentos";

/** Prévia informativa do preço; o POST do agendamento recalcula na API. */
export async function GET(req: Request) {
  const sp = new URL(req.url).searchParams;
  const petId = sp.get("petId") ?? "";
  const servicoId = sp.get("servicoId") ?? "";
  if (!/^\d+$/.test(petId) || !/^\d+$/.test(servicoId)) return new Response(null, { status: 400 });
  try {
    return Response.json(await apiFetch<PrecoPrevisto>(`/api/v1/agendamentos/preco?petId=${petId}&servicoId=${servicoId}`));
  } catch (e) {
    if (e instanceof ApiError) return new Response(null, { status: e.problem.status });
    throw e;
  }
}
