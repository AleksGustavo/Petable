import { mkdirSync, writeFileSync } from "node:fs";
import { randomBytes } from "node:crypto";
import path from "node:path";
import { chamar, tokenAdmin } from "../e2e/api";

export const ARQUIVO_PACOTE = path.join(__dirname, "..", ".e2e", "pacote.json");

/** Um usuário por papel (ADMIN, VENDAS, SERVICO) efêmeros: o que cada um vê depende do módulo E do papel. */
export type EstadoPacote = { sufixo: string; usuarios: Record<"ADMIN" | "VENDAS" | "SERVICO", { id: number; username: string; senha: string }> };

export default async function globalSetup() {
  const token = await tokenAdmin();
  const sufixo = Date.now().toString(36);
  const usuarios = {} as EstadoPacote["usuarios"];
  for (const papel of ["ADMIN", "VENDAS", "SERVICO"] as const) {
    const senha = `E2e-${randomBytes(9).toString("base64url")}9a`;
    const username = `pkg.${papel.toLowerCase()}.${sufixo}`;
    const u = await chamar<{ id: number }>("POST", "/api/v1/usuarios", token, {
      username,
      email: `${username}@petconnect.test`,
      senha,
      papel,
    });
    usuarios[papel] = { id: u.id, username, senha };
  }
  mkdirSync(path.dirname(ARQUIVO_PACOTE), { recursive: true });
  writeFileSync(ARQUIVO_PACOTE, JSON.stringify({ sufixo, usuarios }), { encoding: "utf-8", mode: 0o600 });
}
