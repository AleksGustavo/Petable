import "server-only";
import { redirect } from "next/navigation";
import { getSessao } from "@/lib/session";

/** Corpo de erro RFC 9457 devolvido pela API Spring. */
export type Problem = { title?: string; detail?: string; status: number; errors?: Record<string, string> };

export class ApiError extends Error {
  constructor(public readonly problem: Problem) {
    super(problem.detail ?? problem.title ?? `Erro ${problem.status}`);
  }
}

export function apiUrl(path: string): string {
  const base = process.env.API_URL ?? "http://localhost:8081";
  return `${base.replace(/\/$/, "")}${path}`;
}

/**
 * Chamada autenticada à API a partir do servidor do Next (Server Component ou
 * Server Action). O token vem do cookie httpOnly e nunca chega ao navegador.
 */
export async function apiFetch<T>(path: string, init: { method?: string; body?: unknown } = {}): Promise<T> {
  const sessao = await getSessao();
  if (!sessao) redirect("/login");

  const res = await fetch(apiUrl(path), {
    method: init.method ?? "GET",
    headers: {
      Authorization: `Bearer ${sessao.token}`,
      ...(init.body !== undefined && { "Content-Type": "application/json" }),
    },
    body: init.body !== undefined ? JSON.stringify(init.body) : undefined,
    cache: "no-store",
  });

  if (res.status === 401) redirect("/login");
  if (!res.ok) {
    const problem = (await res.json().catch(() => ({}))) as Partial<Problem>;
    throw new ApiError({ ...problem, status: res.status });
  }
  return (res.status === 204 ? undefined : await res.json()) as T;
}
