import { NextRequest } from "next/server";
import { describe, expect, it } from "vitest";
import { proxy } from "./proxy";

function token(exp: number) {
  const b64 = (o: object) => Buffer.from(JSON.stringify(o)).toString("base64url");
  return `${b64({ alg: "HS256" })}.${b64({ sub: "ana", uid: 1, tv: 0, roles: ["VENDAS"], exp })}.x`;
}

function req(caminho: string, comSessao: boolean) {
  const r = new NextRequest(new URL(caminho, "http://localhost:3000"));
  if (comSessao) r.cookies.set("pc_session", token(Math.floor(Date.now() / 1000) + 3600));
  return r;
}

describe("proxy", () => {
  it("sessão revogada na API: /login?expirada apaga o cookie em vez de voltar ao painel", () => {
    const res = proxy(req("/login?expirada=1", true));
    expect(res.headers.get("location")).toBeNull();
    expect(res.headers.get("set-cookie")).toMatch(/pc_session=;/);
  });

  it("recuperação de senha é pública", () => {
    expect(proxy(req("/esqueci-senha", false)).headers.get("location")).toBeNull();
    expect(proxy(req("/redefinir-senha?token=x", false)).headers.get("location")).toBeNull();
  });

  it("sem sessão vai para o login e com sessão sai do login", () => {
    expect(proxy(req("/usuarios", false)).headers.get("location")).toContain("/login?next=%2Fusuarios");
    expect(proxy(req("/login", true)).headers.get("location")).toContain("/dashboard");
  });
});
