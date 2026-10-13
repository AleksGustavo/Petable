"use server";

import * as z from "zod";
import { revalidatePath } from "next/cache";
import { redirect, unstable_rethrow } from "next/navigation";
import { ApiError, apiFetch } from "@/lib/api";
import type { FormState } from "@/lib/clientes";
import { mensagemDeErro } from "@/lib/erros";
import { consultaSchema, erroDaFoto, petSchema, vacinaSchema, type Pet } from "@/lib/pets";

function valoresDo(formData: FormData): Record<string, string> {
  return Object.fromEntries([...formData.entries()].filter(([, v]) => typeof v === "string")) as Record<string, string>;
}

function errosDoZod(error: z.ZodError): Record<string, string> {
  return Object.fromEntries(
    Object.entries(z.flattenError(error).fieldErrors).map(([k, v]) => [k, (v as string[] | undefined)?.[0] ?? "Inválido"]),
  );
}

/** Converte falha da API/rede em estado de formulário (400 vira erro no campo). */
function estadoDeFalha(e: unknown, valores: Record<string, string>, recurso?: string): FormState {
  unstable_rethrow(e);
  if (e instanceof ApiError) {
    const { status, detail, errors } = e.problem;
    return { mensagem: mensagemDeErro(status, detail, recurso), erros: errors, valores };
  }
  return { mensagem: mensagemDeErro(0), valores };
}

function fotoEnviada(formData: FormData): File | null {
  const f = formData.get("foto");
  return f instanceof File && f.size > 0 ? f : null;
}

export async function salvarPet(id: number | null, _prev: FormState, formData: FormData): Promise<FormState> {
  const valores = valoresDo(formData);
  const parsed = petSchema.safeParse(valores);
  const foto = fotoEnviada(formData);
  const erroFoto = foto ? erroDaFoto(foto) : null;
  if (!parsed.success || erroFoto) {
    return {
      erros: { ...(parsed.success ? {} : errosDoZod(parsed.error)), ...(erroFoto && { foto: erroFoto }) },
      valores,
    };
  }

  let salvo: Pet;
  try {
    salvo = await apiFetch<Pet>(id ? `/api/v1/pets/${id}` : "/api/v1/pets", {
      method: id ? "PUT" : "POST",
      body: parsed.data,
    });
  } catch (e) {
    return estadoDeFalha(e, valores);
  }

  // Foto em segundo passo: se falhar, o cadastro do pet continua valendo.
  let fotoFalhou = false;
  if (foto) {
    const multipart = new FormData();
    multipart.set("foto", foto);
    try {
      await apiFetch(`/api/v1/pets/${salvo.id}/foto`, { method: "PUT", body: multipart });
    } catch (e) {
      unstable_rethrow(e);
      fotoFalhou = true;
    }
  }

  revalidatePath("/pets");
  revalidatePath(`/clientes/${salvo.tutor.id}`);
  redirect(`/pets/${salvo.id}?${fotoFalhou ? "foto=erro" : "salvo=1"}`);
}

export async function desativarPet(id: number, clienteId: number) {
  await apiFetch<void>(`/api/v1/pets/${id}`, { method: "DELETE" });
  revalidatePath("/pets");
  revalidatePath(`/clientes/${clienteId}`);
  redirect("/pets?desativado=1");
}

export async function registrarVacina(petId: number, _prev: FormState, formData: FormData): Promise<FormState> {
  const valores = valoresDo(formData);
  const parsed = vacinaSchema.safeParse(valores);
  if (!parsed.success) return { erros: errosDoZod(parsed.error), valores };
  try {
    await apiFetch(`/api/v1/pets/${petId}/vacinas`, { method: "POST", body: parsed.data });
  } catch (e) {
    return estadoDeFalha(e, valores);
  }
  revalidatePath(`/pets/${petId}`);
  redirect(`/pets/${petId}?aba=vacinas&ok=vacina`);
}

export async function removerVacina(petId: number, lancamentoId: number) {
  await apiFetch<void>(`/api/v1/pets/${petId}/vacinas/${lancamentoId}`, { method: "DELETE" });
  revalidatePath(`/pets/${petId}`);
  redirect(`/pets/${petId}?aba=vacinas&ok=removida`);
}

export async function registrarConsulta(petId: number, _prev: FormState, formData: FormData): Promise<FormState> {
  const valores = valoresDo(formData);
  const parsed = consultaSchema.safeParse(valores);
  if (!parsed.success) return { erros: errosDoZod(parsed.error), valores };
  try {
    await apiFetch(`/api/v1/pets/${petId}/consultas`, { method: "POST", body: parsed.data });
  } catch (e) {
    return estadoDeFalha(e, valores);
  }
  revalidatePath(`/pets/${petId}`);
  redirect(`/pets/${petId}?aba=consultas&ok=consulta`);
}
