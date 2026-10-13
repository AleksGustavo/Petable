"use server";

import * as z from "zod";
import { revalidatePath } from "next/cache";
import { redirect, unstable_rethrow } from "next/navigation";
import { ApiError, apiFetch, apiRequest } from "@/lib/api";
import type { FormState } from "@/lib/clientes";
import { mensagemDeErro } from "@/lib/erros";
import { cancelamentoSchema, errosPorCaminho } from "@/lib/estoque";
import { vendaSchema, type PreviaVenda, type Venda, type VendaEntrada } from "@/lib/vendas";

export type FalhaVenda = {
  ok: false;
  status: number;
  mensagem: string;
  erros?: Record<string, string>;
  /** 409 de estoque: produto que faltou (a linha é destacada no PDV). */
  produtoId?: number;
};

export type RespostaPrevia = { ok: true; previa: PreviaVenda } | FalhaVenda;
export type RespostaVenda = { ok: true; venda: Venda; repetida: boolean } | FalhaVenda;

function falha(e: unknown): FalhaVenda {
  unstable_rethrow(e);
  if (e instanceof ApiError) {
    const { status, detail, errors, produtoId } = e.problem;
    return { ok: false, status, mensagem: mensagemDeErro(status, detail, "Venda"), erros: errors, produtoId };
  }
  return { ok: false, status: 0, mensagem: mensagemDeErro(0) };
}

function invalida(error: z.ZodError): FalhaVenda {
  const erros = errosPorCaminho(error);
  return { ok: false, status: 400, mensagem: Object.values(erros)[0] ?? "Verifique os dados da venda.", erros };
}

/** Números do backend antes de finalizar (DA-12): não grava nem trava. */
export async function previaVenda(entrada: VendaEntrada): Promise<RespostaPrevia> {
  const parsed = vendaSchema.safeParse(entrada);
  if (!parsed.success) return invalida(parsed.error);
  try {
    return { ok: true, previa: await apiFetch<PreviaVenda>("/api/v1/vendas/previa", { method: "POST", body: parsed.data }) };
  } catch (e) {
    return falha(e);
  }
}

/** Finaliza com a Idempotency-Key do carrinho: reenviar devolve a mesma venda (200) sem nova baixa. */
export async function finalizarVenda(entrada: VendaEntrada, chave: string): Promise<RespostaVenda> {
  const parsed = vendaSchema.safeParse(entrada);
  if (!parsed.success) return invalida(parsed.error);
  if (!z.uuid().safeParse(chave).success) return { ok: false, status: 400, mensagem: "Chave da venda inválida. Recarregue o PDV." };
  try {
    const r = await apiRequest<Venda>("/api/v1/vendas", {
      method: "POST",
      body: parsed.data,
      headers: { "Idempotency-Key": chave },
    });
    revalidatePath("/vendas");
    revalidatePath("/dashboard");
    revalidatePath("/produtos");
    return { ok: true, venda: r.data, repetida: r.status === 200 };
  } catch (e) {
    return falha(e);
  }
}

export async function cancelarVenda(id: number, _prev: FormState, formData: FormData): Promise<FormState> {
  const valores = { motivo: String(formData.get("motivo") ?? "") };
  const parsed = cancelamentoSchema.safeParse(valores);
  if (!parsed.success) return { erros: { motivo: parsed.error.issues[0]?.message ?? "Motivo inválido" }, valores };
  try {
    await apiFetch(`/api/v1/vendas/${id}/cancelar`, { method: "POST", body: parsed.data });
  } catch (e) {
    unstable_rethrow(e);
    if (e instanceof ApiError) {
      const { status, detail, errors } = e.problem;
      return { mensagem: mensagemDeErro(status, detail, "Venda"), erros: errors, valores };
    }
    return { mensagem: mensagemDeErro(0), valores };
  }
  revalidatePath("/vendas");
  revalidatePath(`/vendas/${id}`);
  revalidatePath("/dashboard");
  redirect(`/vendas/${id}?ok=cancelada`);
}
