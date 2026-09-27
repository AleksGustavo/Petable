import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";

/** Estado compartilhado entre setup, testes e teardown (arquivo fora do git). */
export type EstadoE2E = {
  sufixo: string;
  usuario: { id: number; username: string; senha: string };
  produto: { id: number; nome: string; codigo: string; preco: string };
};

const ARQUIVO = path.join(__dirname, "..", ".e2e", "estado.json");
export const API = process.env.E2E_API_URL ?? "http://localhost:8081";

export function obrigatoria(nome: string): string {
  const v = process.env[nome];
  if (!v) throw new Error(`Defina ${nome} para rodar o E2E.`);
  return v;
}

export async function chamar<T>(metodo: string, caminho: string, token?: string, corpo?: unknown): Promise<T> {
  const res = await fetch(`${API}${caminho}`, {
    method: metodo,
    headers: { "Content-Type": "application/json", ...(token && { Authorization: `Bearer ${token}` }) },
    body: corpo === undefined ? undefined : JSON.stringify(corpo),
  });
  if (!res.ok) throw new Error(`${metodo} ${caminho} → ${res.status}`);
  return (res.status === 204 ? undefined : await res.json()) as T;
}

export async function tokenAdmin(): Promise<string> {
  const r = await chamar<{ accessToken: string }>("POST", "/api/v1/auth/login", undefined, {
    login: obrigatoria("E2E_ADMIN_USERNAME"),
    senha: obrigatoria("E2E_ADMIN_PASSWORD"),
  });
  return r.accessToken;
}

export function salvarEstado(e: EstadoE2E) {
  mkdirSync(path.dirname(ARQUIVO), { recursive: true });
  writeFileSync(ARQUIVO, JSON.stringify(e), { encoding: "utf-8", mode: 0o600 });
}

export function lerEstado(): EstadoE2E {
  return JSON.parse(readFileSync(ARQUIVO, "utf-8")) as EstadoE2E;
}
