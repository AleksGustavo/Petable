"use server";

import * as z from "zod";
import { revalidatePath } from "next/cache";
import { redirect, unstable_rethrow } from "next/navigation";
import { agendamentoSchema, servicoSchema, type Agendamento, type Servico } from "@/lib/agendamentos";
import { ApiError, apiFetch } from "@/lib/api";
import type { FormState } from "@/lib/clientes";
import { mensagemDeErro } from "@/lib/erros";

function valoresDo(formData: FormData): Record<string, string> {
  return Object.fromEntries([...formData.entries()].filter(([, v]) => typeof v === "string")) as Record<string, string>;
}

function errosDoZod(error: z.ZodError): Record<string, string> {
  return Object.fromEntries(
    Object.entries(z.flattenError(error).fieldErrors).map(([k, v]) => [k, (v as string[] | undefined)?.[0] ?? "Inválido"]),
  );
}

/** Erros "vacina.dataAplicacao" da API viram "dataAplicacao" (nome do campo no formulário). */
function errosDaApi(errors: Record<string, string> | undefined): Record<string, string> | undefined {
  return errors && Object.fromEntries(Object.entries(errors).map(([k, v]) => [k.replace(/^vacina\./, ""), v]));
}

function falha(e: unknown, valores: Record<string, string>, recurso: string): FormState {
  unstable_rethrow(e);
  if (e instanceof ApiError) {
    const { status, detail, errors } = e.problem;
    return { mensagem: mensagemDeErro(status, detail, recurso), erros: errosDaApi(errors), valores };
  }
  return { mensagem: mensagemDeErro(0), valores };
}

export async function salvarAgendamento(id: number | null, _prev: FormState, formData: FormData): Promise<FormState> {
  const valores = valoresDo(formData);
  const parsed = agendamentoSchema.safeParse(valores);
  if (!parsed.success) return { erros: errosDoZod(parsed.error), valores };

  let salvo: Agendamento;
  try {
    salvo = await apiFetch<Agendamento>(id ? `/api/v1/agendamentos/${id}` : "/api/v1/agendamentos", {
      method: id ? "PUT" : "POST",
      body: parsed.data,
    });
  } catch (e) {
    return falha(e, valores, "Agendamento");
  }
  revalidatePath("/agendamentos");
  revalidatePath(`/pets/${salvo.pet.id}`);
  redirect(`/agendamentos/${salvo.id}?ok=${id ? "salvo" : "criado"}`);
}

const opcional = (v: string | undefined) => (v?.trim() ? v.trim() : null);

/** Uma action para todas as transições; o formulário manda `status` e os campos da transição. */
export async function alterarStatus(id: number, _prev: FormState, formData: FormData): Promise<FormState> {
  const valores = valoresDo(formData);
  const status = valores.status;
  const body: Record<string, unknown> = { status };

  if (status === "CANCELADO" || status === "NAO_COMPARECEU") {
    body.motivo = opcional(valores.motivo);
    if (status === "CANCELADO" && (!body.motivo || String(body.motivo).length < 3)) {
      return { erros: { motivo: "Informe o motivo do cancelamento (mínimo 3 caracteres)." }, valores };
    }
  }
  if (status === "CONCLUIDO") {
    // Etapa 06: a conclusão vira receita no caixa de quem conclui — forma de pagamento obrigatória.
    const formas = ["DINHEIRO", "CARTAO_CREDITO", "CARTAO_DEBITO", "PIX", "TRANSFERENCIA"];
    if (!formas.includes(valores.formaPagamento ?? "")) {
      return { erros: { formaPagamento: "Selecione a forma de pagamento do serviço." }, valores };
    }
    body.formaPagamento = valores.formaPagamento;
    const valorFinal = opcional(valores.valorFinal);
    if (valorFinal !== null && (Number.isNaN(Number(valorFinal.replace(",", "."))) || Number(valorFinal.replace(",", ".")) < 0)) {
      return { erros: { valorFinal: "Informe um valor não negativo." }, valores };
    }
    body.valorFinal = valorFinal === null ? null : Number(valorFinal.replace(",", "."));
    body.observacoesConclusao = opcional(valores.observacoesConclusao);
    if (valores.vacinacao === "1") {
      if (!valores.vacinaId) return { erros: { vacinaId: "Selecione a vacina aplicada." }, valores };
      body.vacina = {
        vacinaId: Number(valores.vacinaId),
        dataAplicacao: valores.dataAplicacao,
        dataProxima: opcional(valores.dataProxima),
        veterinario: opcional(valores.veterinario),
        lote: opcional(valores.lote),
        observacoes: opcional(valores.observacoesVacina),
      };
    }
  }

  try {
    await apiFetch(`/api/v1/agendamentos/${id}/status`, { method: "POST", body });
  } catch (e) {
    return falha(e, valores, "Agendamento");
  }
  revalidatePath("/agendamentos");
  revalidatePath(`/agendamentos/${id}`);
  redirect(`/agendamentos/${id}?ok=${status}`);
}

export async function salvarServico(id: number | null, _prev: FormState, formData: FormData): Promise<FormState> {
  const valores = valoresDo(formData);
  const parsed = servicoSchema.safeParse(valores);
  if (!parsed.success) return { erros: errosDoZod(parsed.error), valores };
  let salvo: Servico;
  try {
    salvo = await apiFetch<Servico>(id ? `/api/v1/servicos/${id}` : "/api/v1/servicos", {
      method: id ? "PUT" : "POST",
      body: parsed.data,
    });
  } catch (e) {
    return falha(e, valores, "Serviço");
  }
  revalidatePath("/servicos");
  redirect(`/servicos/${salvo.id}?ok=1`);
}

export async function desativarServico(id: number) {
  await apiFetch<void>(`/api/v1/servicos/${id}`, { method: "DELETE" });
  revalidatePath("/servicos");
  redirect(`/servicos/${id}?desativado=1`);
}
