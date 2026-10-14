import "server-only";
import { headers } from "next/headers";
import { redirect } from "next/navigation";
import { getSessao } from "@/lib/session";

/** Corpo de erro RFC 9457 devolvido pela API Spring (409 de estoque traz produtoId/disponivel/solicitado). */
export type Problem = {
  title?: string;
  /** `MODULO_INDISPONIVEL`: 403 uniforme de recurso que esta instalação não contratou (etapa 07). */
  codigo?: string;
  detail?: string;
  status: number;
  errors?: Record<string, string>;
  produtoId?: number;
  disponivel?: number;
  solicitado?: number;
};

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
 * Chamada autenticada à API a partir do servidor do Next (Server Component,
 * Server Action ou Route Handler). O token vem do cookie httpOnly e nunca
 * chega ao navegador. `body` em FormData vai como multipart; o resto, JSON.
 */
export async function apiFetch<T>(path: string, init: { method?: string; body?: unknown } = {}): Promise<T> {
  return (await apiRequest<T>(path, init)).data;
}

/** Como {@link apiFetch}, mas devolve também status e cabeçalhos (ex.: replay idempotente da venda). */
export async function apiRequest<T>(
  path: string,
  init: { method?: string; body?: unknown; headers?: Record<string, string> } = {},
): Promise<{ data: T; status: number; headers: Headers }> {
  const sessao = await getSessao();
  if (!sessao) redirect("/login");

  const multipart = init.body instanceof FormData;
  const requestId = await requestIdAtual();
  const res = await fetch(apiUrl(path), {
    method: init.method ?? "GET",
    headers: {
      Authorization: `Bearer ${sessao.token}`,
      ...(requestId && { "X-Request-Id": requestId }),
      ...(init.body !== undefined && !multipart && { "Content-Type": "application/json" }),
      ...init.headers,
    },
    body: multipart ? (init.body as FormData) : init.body !== undefined ? JSON.stringify(init.body) : undefined,
    cache: "no-store",
  });

  // Sessão revogada na API (senha/papel/desativação) ou expirada: o proxy apaga o cookie.
  if (res.status === 401) redirect("/login?expirada=1");
  if (!res.ok) {
    const problem = (await res.json().catch(() => ({}))) as Partial<Problem>;
    throw new ApiError({ ...problem, status: res.status });
  }
  const data = (res.status === 204 ? undefined : await res.json()) as T;
  return { data, status: res.status, headers: res.headers };
}

/** X-Request-Id da requisição atual (definido no proxy.ts), para correlacionar os logs Next ↔ API. */
export async function requestIdAtual(): Promise<string | null> {
  try {
    return (await headers()).get("x-request-id");
  } catch {
    return null; // fora de uma requisição (ex.: build)
  }
}

/** IP original do navegador, para os limites por IP da API (login, recuperação de senha). */
export async function ipDoNavegador(): Promise<string | null> {
  try {
    const h = await headers();
    return h.get("x-forwarded-for")?.split(",")[0].trim() || h.get("x-real-ip");
  } catch {
    return null;
  }
}
