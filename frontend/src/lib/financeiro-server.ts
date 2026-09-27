import "server-only";
import { notFound } from "next/navigation";
import { ApiError, apiFetch } from "@/lib/api";
import type { ResumoCaixaAtual } from "@/lib/financeiro";

/** Situação do caixa do usuário logado (todos os papéis podem consultar o próprio). */
export async function resumoCaixaAtual(): Promise<ResumoCaixaAtual> {
  return apiFetch<ResumoCaixaAtual>("/api/v1/caixas/resumo-atual");
}

/** GET com 404 da API virando notFound() (ids inválidos também). */
export async function carregar<T>(caminho: string, id: string): Promise<T> {
  if (!/^\d+$/.test(id)) notFound();
  try {
    return await apiFetch<T>(`${caminho}/${id}`);
  } catch (e) {
    if (e instanceof ApiError && e.problem.status === 404) notFound();
    throw e;
  }
}
