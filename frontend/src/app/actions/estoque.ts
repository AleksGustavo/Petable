"use server";

import * as z from "zod";
import { revalidatePath } from "next/cache";
import { redirect, unstable_rethrow } from "next/navigation";
import { ApiError, apiFetch } from "@/lib/api";
import { errosDaApi, type FormState } from "@/lib/clientes";
import { mensagemDeErro } from "@/lib/erros";
import {
  ajusteEstoqueSchema,
  cancelamentoSchema,
  compraSchema,
  errosPorCaminho,
  fornecedorSchema,
  produtoAtualizacaoSchema,
  produtoSchema,
  type Compra,
  type Fornecedor,
  type Produto,
} from "@/lib/estoque";
import { baixaLoteSchema, lotesDoRecebimento, motivoLoteSchema, paraPayload, regularizacaoSchema, validarLinhas } from "@/lib/lotes";
import { hojeISO } from "@/lib/datas";

function valoresDo(formData: FormData): Record<string, string> {
  return Object.fromEntries([...formData.entries()].filter(([, v]) => typeof v === "string")) as Record<string, string>;
}

function errosDoZod(error: z.ZodError): Record<string, string> {
  return Object.fromEntries(
    Object.entries(z.flattenError(error).fieldErrors).map(([k, v]) => [k, (v as string[] | undefined)?.[0] ?? "Inválido"]),
  );
}

function falha(e: unknown, valores: Record<string, string>, recurso: string): FormState {
  unstable_rethrow(e);
  if (e instanceof ApiError) {
    const { status, detail, errors } = e.problem;
    return { mensagem: mensagemDeErro(status, detail, recurso), erros: errosDaApi(errors), valores };
  }
  return { mensagem: mensagemDeErro(0), valores };
}

// ------------------------------------------------------------------ produtos

export async function salvarProduto(id: number | null, _prev: FormState, formData: FormData): Promise<FormState> {
  const valores = valoresDo(formData);
  const parsed = (id ? produtoAtualizacaoSchema : produtoSchema).safeParse(valores);
  if (!parsed.success) return { erros: errosDoZod(parsed.error), valores };
  let salvo: Produto;
  try {
    salvo = await apiFetch<Produto>(id ? `/api/v1/produtos/${id}` : "/api/v1/produtos", {
      method: id ? "PUT" : "POST",
      body: parsed.data,
    });
  } catch (e) {
    return falha(e, valores, "Produto");
  }
  revalidatePath("/produtos");
  redirect(`/produtos/${salvo.id}?ok=${id ? "salvo" : "criado"}`);
}

export async function alternarAtivoProduto(id: number, ativar: boolean): Promise<FormState> {
  try {
    await apiFetch(`/api/v1/produtos/${id}/${ativar ? "ativar" : "desativar"}`, { method: "PATCH" });
  } catch (e) {
    return falha(e, {}, "Produto");
  }
  revalidatePath("/produtos");
  revalidatePath(`/produtos/${id}`);
  redirect(`/produtos/${id}?ok=${ativar ? "ativado" : "desativado"}`);
}

export async function ajustarEstoque(id: number, _prev: FormState, formData: FormData): Promise<FormState> {
  const valores = valoresDo(formData);
  const parsed = ajusteEstoqueSchema.safeParse(valores);
  if (!parsed.success) return { erros: errosDoZod(parsed.error), valores };
  try {
    await apiFetch(`/api/v1/produtos/${id}/ajustes-estoque`, { method: "POST", body: parsed.data });
  } catch (e) {
    return falha(e, valores, "Produto");
  }
  revalidatePath("/produtos");
  revalidatePath(`/produtos/${id}`);
  redirect(`/produtos/${id}?aba=estoque&ok=ajuste`);
}

// ------------------------------------------------------------------ lotes (etapa 04)

/** Liga o controle de lote e distribui o saldo atual pelos lotes informados (soma exata). */
export async function ativarControleLote(id: number, saldo: number, _prev: FormState, formData: FormData): Promise<FormState> {
  const valores = valoresDo(formData);
  const parsed = regularizacaoSchema(saldo).safeParse(valores);
  if (!parsed.success) return { erros: errosPorCaminho(parsed.error), mensagem: "Confira os lotes informados.", valores };
  try {
    await apiFetch(`/api/v1/produtos/${id}/controle-lote`, { method: "POST", body: parsed.data });
  } catch (e) {
    return falha(e, valores, "Produto");
  }
  revalidatePath("/produtos");
  revalidatePath(`/produtos/${id}`);
  redirect(`/produtos/${id}?aba=lotes&ok=controle`);
}

export type AcaoLote = "bloquear" | "desbloquear" | "perdas" | "vencimentos";

/** Bloquear/desbloquear (só motivo) e perda/baixa por vencimento (quantidade + motivo). A API confere o papel ADMIN. */
export async function acaoSobreLote(produtoId: number, loteId: number, acao: AcaoLote, _prev: FormState, formData: FormData): Promise<FormState> {
  const valores = valoresDo(formData);
  const comQuantidade = acao === "perdas" || acao === "vencimentos";
  const parsed = (comQuantidade ? baixaLoteSchema : motivoLoteSchema).safeParse(valores);
  if (!parsed.success) return { erros: errosDoZod(parsed.error), valores };
  try {
    await apiFetch(`/api/v1/produtos/${produtoId}/lotes/${loteId}/${acao}`, { method: "POST", body: parsed.data });
  } catch (e) {
    return falha(e, valores, "Lote");
  }
  revalidatePath("/produtos");
  revalidatePath(`/produtos/${produtoId}`);
  revalidatePath("/estoque/lotes");
  redirect(`/produtos/${produtoId}?aba=lotes&ok=${acao}`);
}

/** Liga uma vacina do catálogo a este produto: aplicar a vacina passa a baixar uma dose do lote que vence primeiro. */
export async function vincularVacina(produtoId: number, _prev: FormState, formData: FormData): Promise<FormState> {
  const valores = valoresDo(formData);
  const vacinaId = /^\d+$/.test(valores.vacinaId ?? "") ? Number(valores.vacinaId) : null;
  if (!vacinaId) return { erros: { vacinaId: "Selecione a vacina" }, valores };
  try {
    await apiFetch(`/api/v1/vacinas/${vacinaId}/produto`, { method: "PUT", body: { produtoId } });
  } catch (e) {
    return falha(e, valores, "Vacina");
  }
  revalidatePath(`/produtos/${produtoId}`);
  redirect(`/produtos/${produtoId}?aba=lotes&ok=vacina`);
}

export async function desvincularVacina(produtoId: number, vacinaId: number): Promise<FormState> {
  try {
    await apiFetch(`/api/v1/vacinas/${vacinaId}/produto`, { method: "DELETE" });
  } catch (e) {
    return falha(e, {}, "Vacina");
  }
  revalidatePath(`/produtos/${produtoId}`);
  redirect(`/produtos/${produtoId}?aba=lotes&ok=vacinaRemovida`);
}

// ------------------------------------------------------------------ fornecedores

export async function salvarFornecedor(id: number | null, _prev: FormState, formData: FormData): Promise<FormState> {
  const valores = valoresDo(formData);
  const parsed = fornecedorSchema.safeParse(valores);
  if (!parsed.success) return { erros: errosDoZod(parsed.error), valores };
  let salvo: Fornecedor;
  try {
    salvo = await apiFetch<Fornecedor>(id ? `/api/v1/fornecedores/${id}` : "/api/v1/fornecedores", {
      method: id ? "PUT" : "POST",
      body: parsed.data,
    });
  } catch (e) {
    return falha(e, valores, "Fornecedor");
  }
  revalidatePath("/fornecedores");
  redirect(`/fornecedores/${salvo.id}?ok=${id ? "salvo" : "criado"}`);
}

export async function alternarAtivoFornecedor(id: number, ativar: boolean): Promise<FormState> {
  try {
    await apiFetch(`/api/v1/fornecedores/${id}/${ativar ? "ativar" : "desativar"}`, { method: "PATCH" });
  } catch (e) {
    return falha(e, {}, "Fornecedor");
  }
  revalidatePath("/fornecedores");
  revalidatePath(`/fornecedores/${id}`);
  redirect(`/fornecedores/${id}?ok=${ativar ? "ativado" : "desativado"}`);
}

// ------------------------------------------------------------------ compras

export async function criarCompra(_prev: FormState, formData: FormData): Promise<FormState> {
  const valores = valoresDo(formData);
  let itens: unknown;
  try {
    itens = JSON.parse(valores.itens || "[]");
  } catch {
    itens = [];
  }
  const parsed = compraSchema.safeParse({ ...valores, itens });
  if (!parsed.success) return { erros: errosPorCaminho(parsed.error), valores };
  let criada: Compra;
  try {
    criada = await apiFetch<Compra>("/api/v1/compras", { method: "POST", body: parsed.data });
  } catch (e) {
    return falha(e, valores, "Compra");
  }
  revalidatePath("/compras");
  redirect(`/compras/${criada.id}?ok=criada`);
}

export async function receberCompra(id: number, _prev: FormState, formData: FormData): Promise<FormState> {
  const dataEntrega = String(formData.get("dataEntrega") ?? "");
  const dataVencimento = String(formData.get("dataVencimento") ?? "");
  const valores = { dataEntrega, dataVencimento };
  if (!/^\d{4}-\d{2}-\d{2}$/.test(dataEntrega)) return { erros: { dataEntrega: "Informe a data de entrega" }, valores };
  // Etapa 06: vencimento da conta a pagar entre a entrega e 365 dias depois (a API confere).
  if (!/^\d{4}-\d{2}-\d{2}$/.test(dataVencimento)) return { erros: { dataVencimento: "Informe o vencimento da conta" }, valores };
  if (dataVencimento < dataEntrega) {
    return { erros: { dataVencimento: "O vencimento não pode ser anterior à entrega." }, valores };
  }
  // Lotes (etapa 04): uma grade por item de produto controlado. A soma de cada grade tem de bater com a quantidade do item
  // (conferida na tela, aqui e na API); o servidor é quem decide, e a mensagem dele aparece sem ser escondida.
  const porProduto = lotesDoRecebimento(String(formData.get("lotes") ?? ""));
  // "produtoId:quantidade:exigeValidade" por item controlado.
  const itens = new Map<number, { esperado: number; exigeValidade: boolean }>(
    String(formData.get("quantidades") ?? "")
      .split(",")
      .filter(Boolean)
      .map((par) => {
        const [produtoId, esperado, exige] = par.split(":").map(Number);
        return [produtoId, { esperado, exigeValidade: exige !== 0 }] as [number, { esperado: number; exigeValidade: boolean }];
      }),
  );
  const erros: Record<string, string> = {};
  for (const grade of porProduto) {
    const item = itens.get(grade.produtoId) ?? { esperado: 0, exigeValidade: true };
    const e = validarLinhas(grade.linhas, { ...item, recebimento: true }, hojeISO());
    for (const [campo, mensagem] of Object.entries(e)) erros[`lotes.${grade.produtoId}.${campo}`] = mensagem;
  }
  if (Object.keys(erros).length > 0) {
    return { erros, mensagem: "Confira os lotes informados: a soma de cada item precisa ser igual à quantidade comprada.", valores };
  }
  const lotes = porProduto.map((g) => ({ produtoId: g.produtoId, lotes: paraPayload(g.linhas) }));
  try {
    await apiFetch(`/api/v1/compras/${id}/receber`, {
      method: "POST",
      body: lotes.length > 0 ? { dataEntrega, dataVencimento, lotes } : { dataEntrega, dataVencimento },
    });
  } catch (e) {
    return falha(e, valores, "Compra");
  }
  revalidatePath("/compras");
  revalidatePath("/produtos");
  revalidatePath("/financeiro");
  redirect(`/compras/${id}?ok=recebida`);
}

export async function cancelarCompra(id: number, _prev: FormState, formData: FormData): Promise<FormState> {
  const valores = valoresDo(formData);
  const parsed = cancelamentoSchema.safeParse(valores);
  if (!parsed.success) return { erros: errosDoZod(parsed.error), valores };
  try {
    await apiFetch(`/api/v1/compras/${id}/cancelar`, { method: "POST", body: parsed.data });
  } catch (e) {
    return falha(e, valores, "Compra");
  }
  revalidatePath("/compras");
  redirect(`/compras/${id}?ok=cancelada`);
}
