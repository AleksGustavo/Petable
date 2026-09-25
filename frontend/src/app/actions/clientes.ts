"use server";

import * as z from "zod";
import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { ApiError, apiFetch } from "@/lib/api";
import { clienteSchema, errosDaApi, type Cliente, type FormState } from "@/lib/clientes";

export async function salvarCliente(id: number | null, _prev: FormState, formData: FormData): Promise<FormState> {
  const valores = Object.fromEntries(
    [...formData.entries()].filter(([, v]) => typeof v === "string"),
  ) as Record<string, string>;

  const parsed = clienteSchema.safeParse(valores);
  if (!parsed.success) {
    const erros = Object.fromEntries(
      Object.entries(z.flattenError(parsed.error).fieldErrors).map(([k, v]) => [k, v?.[0] ?? "Inválido"]),
    );
    return { erros, valores };
  }

  let salvo: Cliente;
  try {
    salvo = await apiFetch<Cliente>(id ? `/api/v1/clientes/${id}` : "/api/v1/clientes", {
      method: id ? "PUT" : "POST",
      body: parsed.data,
    });
  } catch (e) {
    if (e instanceof ApiError) {
      return { mensagem: e.message, erros: errosDaApi(e.problem.errors), valores };
    }
    throw e;
  }

  revalidatePath("/clientes");
  redirect(`/clientes/${salvo.id}?salvo=1`);
}

export async function inativarCliente(id: number) {
  await apiFetch<void>(`/api/v1/clientes/${id}`, { method: "DELETE" });
  revalidatePath("/clientes");
  redirect("/clientes?inativado=1");
}
