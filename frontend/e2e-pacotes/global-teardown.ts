import { readFileSync } from "node:fs";
import { chamar, tokenAdmin } from "../e2e/api";
import { ARQUIVO_PACOTE, type EstadoPacote } from "./global-setup";

/** Sem hard-delete: os usuários efêmeros são desativados. */
export default async function globalTeardown() {
  const estado = JSON.parse(readFileSync(ARQUIVO_PACOTE, "utf-8")) as EstadoPacote;
  const token = await tokenAdmin();
  for (const u of Object.values(estado.usuarios)) {
    await chamar("PATCH", `/api/v1/usuarios/${u.id}/desativar`, token).catch(() => undefined);
  }
}
