import { chamar, lerEstado, tokenAdmin } from "./api";

/** Sem hard-delete: o usuário efêmero é desativado (e a sessão dele, revogada). */
export default async function globalTeardown() {
  const estado = lerEstado();
  const token = await tokenAdmin();
  await chamar("PATCH", `/api/v1/usuarios/${estado.usuario.id}/desativar`, token).catch(() => undefined);
}
