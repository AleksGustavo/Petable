"use server";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { apiUrl, ipDoNavegador, requestIdAtual } from "@/lib/api";
import { SESSION_COOKIE } from "@/lib/jwt";

export type LoginState = { erro?: string; login?: string };

/** Só aceita caminhos internos, para o ?next= não virar open redirect. */
function destinoSeguro(next: FormDataEntryValue | null): string {
  const s = typeof next === "string" ? next : "";
  return s.startsWith("/") && !s.startsWith("//") ? s : "/dashboard";
}

export async function entrar(_prev: LoginState, formData: FormData): Promise<LoginState> {
  const login = String(formData.get("login") ?? "").trim();
  const senha = String(formData.get("senha") ?? "");
  if (!login || !senha) return { erro: "Informe usuário e senha.", login };

  // IP e request id repassados: a API limita tentativas por IP (etapa 09) e correlaciona os logs.
  const [ip, requestId] = await Promise.all([ipDoNavegador(), requestIdAtual()]);
  let res: Response;
  try {
    res = await fetch(apiUrl("/api/v1/auth/login"), {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...(ip && { "X-Forwarded-For": ip }),
        ...(requestId && { "X-Request-Id": requestId }),
      },
      body: JSON.stringify({ login, senha }),
      cache: "no-store",
    });
  } catch {
    return { erro: "Não foi possível falar com o servidor. Tente de novo em instantes.", login };
  }

  if (!res.ok) {
    const problem = await res.json().catch(() => ({}));
    return { erro: problem.detail ?? "Usuário ou senha inválidos.", login };
  }

  const { accessToken, expiresIn } = (await res.json()) as { accessToken: string; expiresIn: number };
  (await cookies()).set(SESSION_COOKIE, accessToken, {
    httpOnly: true,
    secure: process.env.COOKIE_SECURE !== "false",
    sameSite: "lax",
    path: "/",
    maxAge: expiresIn,
  });
  redirect(destinoSeguro(formData.get("next")));
}

export async function sair() {
  (await cookies()).delete(SESSION_COOKIE);
  redirect("/login");
}
