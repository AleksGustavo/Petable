import "server-only";
import { cache } from "react";
import { apiFetch } from "@/lib/api";
import { CAPACIDADES_DESCONHECIDAS, temIntegracao, temModulo, type Capacidades } from "@/lib/capacidades";

/** Capacidades da instalação, uma consulta por requisição. Nunca vão para o token nem para o cache entre usuários. */
export const carregarCapacidades = cache(async (): Promise<Capacidades> => {
  try {
    return await apiFetch<Capacidades>("/api/v1/modulos");
  } catch {
    return CAPACIDADES_DESCONHECIDAS;
  }
});

export async function moduloAtivo(id: string): Promise<boolean> {
  return temModulo(await carregarCapacidades(), id);
}

export async function integracaoAtiva(id: string): Promise<boolean> {
  return temIntegracao(await carregarCapacidades(), id);
}
