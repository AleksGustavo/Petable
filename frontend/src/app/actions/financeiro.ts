"use server";

import * as z from "zod";
import { revalidatePath } from "next/cache";
import { redirect, unstable_rethrow } from "next/navigation";
import { ApiError, apiFetch, apiRequest } from "@/lib/api";
import type { FormState } from "@/lib/clientes";
import { mensagemDeErro } from "@/lib/erros";
import {
  aberturaCaixaSchema,
  estornoSchema,
  fechamentoSchema,
  lancamentoManualSchema,
  movimentoCaixaSchema,
  pagamentoContaSchema,
  type Caixa,
  type Lancamento,
} from "@/lib/financeiro";

function valoresDo(formData: FormData): Record<string, string> {
  return Object.fromEntries([...formData.entries()].filter(([, v]) => typeof v === "string")) as Record<string, string>;
}

function errosDoZod(error: z.ZodError): Record<string, string> {
  return Object.fromEntries(
    Object.entries(z.flattenError(error).fieldErrors).map(([k, v]) => [k, (v as string[] | undefined)?.[0] ?? "Inválido"]),
  );
}

/** Erros "contado.DINHEIRO" da API viram "contadoDINHEIRO" (nome do campo no formulário). */
function errosDaApi(errors: Record<string, string> | undefined) {
  return errors && Object.fromEntries(Object.entries(errors).map(([k, v]) => [k.replace(/^contado\./, "contado"), v]));
}

function falha(e: unknown, valores: Record<string, string>, recurso: string): FormState {
  unstable_rethrow(e);
  if (e instanceof ApiError) {
    const { status, detail, errors } = e.problem;
    return { mensagem: mensagemDeErro(status, detail, recurso), erros: errosDaApi(errors), valores };
  }
  return { mensagem: mensagemDeErro(0), valores };
}

function revalidarFinanceiro() {
  revalidatePath("/financeiro");
  revalidatePath("/caixa");
  revalidatePath("/dashboard");
}

// ------------------------------------------------------------------ lançamentos (ADMIN)

/** A chave vem do formulário (gerada uma vez no navegador) e é reenviada num retry: não duplica. */
export async function criarLancamento(_prev: FormState, formData: FormData): Promise<FormState> {
  const valores = valoresDo(formData);
  const parsed = lancamentoManualSchema.safeParse(valores);
  if (!parsed.success) return { erros: errosDoZod(parsed.error), valores };
  if (!z.uuid().safeParse(valores.chave).success) return { mensagem: "Chave do lançamento inválida. Recarregue a página.", valores };
  let criado: Lancamento;
  try {
    criado = (await apiRequest<Lancamento>("/api/v1/financeiro/lancamentos", {
      method: "POST",
      body: parsed.data,
      headers: { "Idempotency-Key": valores.chave },
    })).data;
  } catch (e) {
    return falha(e, valores, "Lançamento");
  }
  revalidarFinanceiro();
  redirect(`/financeiro/lancamentos/${criado.id}?ok=criado`);
}

export async function estornarLancamento(id: number, _prev: FormState, formData: FormData): Promise<FormState> {
  const valores = valoresDo(formData);
  const parsed = estornoSchema.safeParse(valores);
  if (!parsed.success) return { erros: errosDoZod(parsed.error), valores };
  let estorno: Lancamento;
  try {
    estorno = await apiFetch<Lancamento>(`/api/v1/financeiro/lancamentos/${id}/estornar`, { method: "POST", body: parsed.data });
  } catch (e) {
    return falha(e, valores, "Lançamento");
  }
  revalidarFinanceiro();
  redirect(`/financeiro/lancamentos/${estorno.id}?ok=estorno`);
}

export async function pagarConta(id: number, _prev: FormState, formData: FormData): Promise<FormState> {
  const valores = valoresDo(formData);
  const parsed = pagamentoContaSchema.safeParse(valores);
  if (!parsed.success) return { erros: errosDoZod(parsed.error), valores };
  try {
    await apiFetch(`/api/v1/financeiro/contas-a-pagar/${id}/pagar`, { method: "POST", body: parsed.data });
  } catch (e) {
    return falha(e, valores, "Conta a pagar");
  }
  revalidarFinanceiro();
  revalidatePath(`/financeiro/contas-a-pagar/${id}`);
  redirect(`/financeiro/contas-a-pagar/${id}?ok=paga`);
}

// ------------------------------------------------------------------ caixa (cada usuário, o próprio)

export async function abrirCaixa(_prev: FormState, formData: FormData): Promise<FormState> {
  const valores = valoresDo(formData);
  const parsed = aberturaCaixaSchema.safeParse(valores);
  if (!parsed.success) return { erros: errosDoZod(parsed.error), valores };
  try {
    await apiFetch("/api/v1/caixas/abrir", { method: "POST", body: parsed.data });
  } catch (e) {
    return falha(e, valores, "Caixa");
  }
  revalidarFinanceiro();
  revalidatePath("/pdv");
  redirect("/caixa?ok=aberto");
}

export async function movimentarCaixa(tipo: "sangrias" | "suprimentos", _prev: FormState, formData: FormData): Promise<FormState> {
  const valores = valoresDo(formData);
  const parsed = movimentoCaixaSchema.safeParse(valores);
  if (!parsed.success) return { erros: errosDoZod(parsed.error), valores };
  try {
    await apiFetch(`/api/v1/caixas/atual/${tipo}`, { method: "POST", body: parsed.data });
  } catch (e) {
    return falha(e, valores, "Caixa");
  }
  revalidarFinanceiro();
  redirect(`/caixa?ok=${tipo === "sangrias" ? "sangria" : "suprimento"}`);
}

/** O esperado usado na validação local chega em campos ocultos (só UX); a API recalcula tudo sob lock. */
export async function fecharCaixa(_prev: FormState, formData: FormData): Promise<FormState> {
  const valores = valoresDo(formData);
  const esperado = JSON.parse(valores.esperado || "{}") as Record<string, number>;
  const parsed = fechamentoSchema(esperado).safeParse(valores);
  if (!parsed.success) return { erros: errosDoZod(parsed.error), valores };
  let fechado: Caixa;
  try {
    fechado = await apiFetch<Caixa>("/api/v1/caixas/atual/fechar", { method: "POST", body: parsed.data });
  } catch (e) {
    return falha(e, valores, "Caixa");
  }
  revalidarFinanceiro();
  revalidatePath("/pdv");
  redirect(`/caixa/${fechado.id}?ok=fechado`);
}
