import "server-only";
import { apiFetch } from "@/lib/api";
import type { CatalogoItem, EspecieComRacas } from "@/lib/pets";

/** Espécies com as raças já carregadas, para o select dependente sem ida extra à API. */
export async function especiesComRacas(): Promise<EspecieComRacas[]> {
  const especies = await apiFetch<CatalogoItem[]>("/api/v1/especies");
  return Promise.all(
    especies.map(async (e) => ({ ...e, racas: await apiFetch<CatalogoItem[]>(`/api/v1/especies/${e.id}/racas`) })),
  );
}
