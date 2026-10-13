import "server-only";
import { notFound } from "next/navigation";
import { ApiError, apiFetch } from "@/lib/api";
import type { Venda } from "@/lib/vendas";

/** Detalhe da venda para páginas (404 da API vira notFound). */
export async function carregarVenda(id: string): Promise<Venda> {
  if (!/^\d+$/.test(id)) notFound();
  try {
    return await apiFetch<Venda>(`/api/v1/vendas/${id}`);
  } catch (e) {
    if (e instanceof ApiError && e.problem.status === 404) notFound();
    throw e;
  }
}
