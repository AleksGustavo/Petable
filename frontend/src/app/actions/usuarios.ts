"use server";

import * as z from "zod";
import { cookies, headers } from "next/headers";
import { revalidatePath } from "next/cache";
import { redirect, unstable_rethrow } from "next/navigation";
import { ApiError, apiFetch, apiUrl } from "@/lib/api";
import type { FormState } from "@/lib/clientes";
import { mensagemDeErro } from "@/lib/erros";
import { SESSION_COOKIE } from "@/lib/jwt";
import {
  contaSchema,
  funcionarioSchema,
  MENSAGEM_RECUPERACAO,
  meuFuncionarioSchema,
  novoUsuarioSchema,
  redefinirSenhaSchema,
  trocarSenhaSchema,
  type Funcionario,
  type Usuario,
} from "@/lib/usuarios";

function valoresDo(formData: FormData): Record<string, string> {
  return Object.fromEntries([...formData.entries()].filter(([, v]) => typeof v === "string")) as Record<string, string>;
}

/** Nunca devolve senha para o formulário (não repopula campos de senha). */
function semSenhas(valores: Record<string, string>) {
  return Object.fromEntries(Object.entries(valores).filter(([k]) => !/senha/i.test(k)));
}

function errosDoZod(error: z.ZodError): Record<string, string> {
  return Object.fromEntries(
    Object.entries(z.flattenError(error).fieldErrors).map(([k, v]) => [k, (v as string[] | undefined)?.[0] ?? "Inválido"]),
  );
}

function errosDaApi(errors: Record<string, string> | undefined) {
  return errors && Object.fromEntries(Object.entries(errors).map(([k, v]) => [k.replace(/^endereco\./, ""), v]));
}

function falha(e: unknown, valores: Record<string, string>, recurso: string): FormState {
  unstable_rethrow(e);
  if (e instanceof ApiError) {
    const { status, detail, errors } = e.problem;
    return { mensagem: mensagemDeErro(status, detail, recurso), erros: errosDaApi(errors), valores: semSenhas(valores) };
  }
  return { mensagem: mensagemDeErro(0), valores: semSenhas(valores) };
}

// ------------------------------------------------------------------ ADMIN

export async function criarUsuario(_prev: FormState, formData: FormData): Promise<FormState> {
  const valores = valoresDo(formData);
  const parsed = novoUsuarioSchema.safeParse(valores);
  if (!parsed.success) return { erros: errosDoZod(parsed.error), valores: semSenhas(valores) };
  let criado: Usuario;
  try {
    criado = await apiFetch<Usuario>("/api/v1/usuarios", { method: "POST", body: parsed.data });
  } catch (e) {
    return falha(e, valores, "Usuário");
  }
  revalidatePath("/usuarios");
  redirect(`/usuarios/${criado.id}?ok=criado`);
}

export async function atualizarContaUsuario(id: number, _prev: FormState, formData: FormData): Promise<FormState> {
  const valores = valoresDo(formData);
  const parsed = contaSchema.safeParse(valores);
  if (!parsed.success) return { erros: errosDoZod(parsed.error), valores };
  try {
    await apiFetch(`/api/v1/usuarios/${id}`, { method: "PUT", body: parsed.data });
  } catch (e) {
    return falha(e, valores, "Usuário");
  }
  revalidatePath(`/usuarios/${id}`);
  redirect(`/usuarios/${id}?ok=conta`);
}

export async function alterarPapelUsuario(id: number, _prev: FormState, formData: FormData): Promise<FormState> {
  const papel = String(formData.get("papel") ?? "");
  try {
    await apiFetch(`/api/v1/usuarios/${id}/papel`, { method: "PATCH", body: { papel } });
  } catch (e) {
    return falha(e, { papel }, "Usuário");
  }
  revalidatePath(`/usuarios/${id}`);
  redirect(`/usuarios/${id}?ok=papel`);
}

export async function alternarAtivoUsuario(id: number, ativar: boolean): Promise<FormState> {
  try {
    await apiFetch(`/api/v1/usuarios/${id}/${ativar ? "ativar" : "desativar"}`, { method: "PATCH" });
  } catch (e) {
    return falha(e, {}, "Usuário");
  }
  revalidatePath("/usuarios");
  revalidatePath(`/usuarios/${id}`);
  redirect(`/usuarios/${id}?ok=${ativar ? "ativado" : "desativado"}`);
}

export async function salvarFuncionario(
  usuarioId: number,
  funcionarioId: number | null,
  _prev: FormState,
  formData: FormData,
): Promise<FormState> {
  const valores = valoresDo(formData);
  const parsed = funcionarioSchema.safeParse(valores);
  if (!parsed.success) return { erros: errosDoZod(parsed.error), valores };
  try {
    await apiFetch<Funcionario>(funcionarioId ? `/api/v1/funcionarios/${funcionarioId}` : "/api/v1/funcionarios", {
      method: funcionarioId ? "PUT" : "POST",
      body: funcionarioId ? parsed.data : { ...parsed.data, usuarioId },
    });
  } catch (e) {
    return falha(e, valores, "Funcionário");
  }
  revalidatePath(`/usuarios/${usuarioId}`);
  redirect(`/usuarios/${usuarioId}?ok=funcionario`);
}

// ------------------------------------------------------------------ perfil

export async function atualizarMinhaConta(_prev: FormState, formData: FormData): Promise<FormState> {
  const valores = valoresDo(formData);
  const parsed = contaSchema.safeParse(valores);
  if (!parsed.success) return { erros: errosDoZod(parsed.error), valores };
  try {
    await apiFetch("/api/v1/me", { method: "PUT", body: parsed.data });
  } catch (e) {
    return falha(e, valores, "Perfil");
  }
  revalidatePath("/perfil");
  redirect("/perfil?ok=conta");
}

export async function atualizarMeuFuncionario(_prev: FormState, formData: FormData): Promise<FormState> {
  const valores = valoresDo(formData);
  const parsed = meuFuncionarioSchema.safeParse(valores);
  if (!parsed.success) return { erros: errosDoZod(parsed.error), valores };
  try {
    await apiFetch("/api/v1/me/funcionario", { method: "PUT", body: parsed.data });
  } catch (e) {
    return falha(e, valores, "Perfil de funcionário");
  }
  revalidatePath("/perfil");
  redirect("/perfil?ok=funcionario");
}

/** Troca a própria senha. A API invalida todas as sessões, então saímos e pedimos novo login. */
export async function trocarMinhaSenha(_prev: FormState, formData: FormData): Promise<FormState> {
  const valores = valoresDo(formData);
  const parsed = trocarSenhaSchema(valores.username, valores.email).safeParse(valores);
  if (!parsed.success) return { erros: errosDoZod(parsed.error) };
  try {
    await apiFetch("/api/v1/me/trocar-senha", { method: "POST", body: parsed.data });
  } catch (e) {
    return falha(e, {}, "Perfil");
  }
  (await cookies()).delete(SESSION_COOKIE);
  redirect("/login?aviso=senha-alterada");
}

// ------------------------------------------------------------------ público

export type RecuperacaoState = { erros?: Record<string, string>; mensagem?: string; enviado?: boolean; sucesso?: boolean };

/** IP do navegador repassado à API (limite por IP); o servidor do Next é o cliente HTTP da API. */
async function ipDoCliente(): Promise<string | null> {
  const h = await headers();
  return h.get("x-forwarded-for")?.split(",")[0].trim() || h.get("x-real-ip");
}

export async function solicitarRedefinicao(_prev: RecuperacaoState, formData: FormData): Promise<RecuperacaoState> {
  const email = String(formData.get("email") ?? "").trim();
  if (!z.email().safeParse(email).success) return { erros: { email: "Informe um e-mail válido." } };
  const ip = await ipDoCliente();
  try {
    await fetch(apiUrl("/api/v1/auth/esqueci-senha"), {
      method: "POST",
      headers: { "Content-Type": "application/json", ...(ip && { "X-Forwarded-For": ip }) },
      body: JSON.stringify({ email }),
      cache: "no-store",
    });
  } catch {
    return { mensagem: mensagemDeErro(0) };
  }
  // Mesma resposta para qualquer e-mail (a API também responde sempre igual).
  return { enviado: true, mensagem: MENSAGEM_RECUPERACAO };
}

export async function redefinirSenha(_prev: RecuperacaoState, formData: FormData): Promise<RecuperacaoState> {
  const valores = valoresDo(formData);
  const parsed = redefinirSenhaSchema.safeParse(valores);
  if (!parsed.success) return { erros: errosDoZod(parsed.error) };
  let res: Response;
  try {
    res = await fetch(apiUrl("/api/v1/auth/redefinir-senha"), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(parsed.data),
      cache: "no-store",
    });
  } catch {
    return { mensagem: mensagemDeErro(0) };
  }
  if (res.status === 204) return { sucesso: true };
  const problem = await res.json().catch(() => ({}));
  return { erros: problem.errors, mensagem: problem.errors?.token ?? problem.detail ?? mensagemDeErro(res.status) };
}
