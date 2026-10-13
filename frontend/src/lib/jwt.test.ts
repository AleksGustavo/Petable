import { describe, expect, it } from "vitest";
import { lerSessao } from "./jwt";

function token(claims: Record<string, unknown>) {
  const b64 = (o: object) => Buffer.from(JSON.stringify(o)).toString("base64url");
  return `${b64({ alg: "HS256" })}.${b64(claims)}.assinatura`;
}

describe("lerSessao", () => {
  const agora = Date.UTC(2026, 0, 1) ;
  const exp = agora / 1000 + 3600;

  it("lê usuário e papel de um token válido", () => {
    const s = lerSessao(token({ sub: "ana", uid: 7, roles: ["VENDAS"], exp }), agora);
    expect(s).toMatchObject({ username: "ana", id: 7, papel: "VENDAS" });
  });

  it("recusa token expirado, sem papel ou malformado", () => {
    expect(lerSessao(token({ sub: "ana", roles: ["VENDAS"], exp: agora / 1000 - 1 }), agora)).toBeNull();
    expect(lerSessao(token({ sub: "ana", exp }), agora)).toBeNull();
    expect(lerSessao("lixo", agora)).toBeNull();
    expect(lerSessao(undefined, agora)).toBeNull();
  });
});
