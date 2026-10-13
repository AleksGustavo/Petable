import { describe, expect, it } from "vitest";
import { contaSchema, novoUsuarioSchema, violacaoSenha } from "./usuarios";

const valido = { username: "maria.silva", email: "maria@empresa.com.br", senha: "Pet@2026Seguro", confirmacaoSenha: "Pet@2026Seguro", papel: "VENDAS" };

describe("usuário", () => {
  it("aceita usuário válido e não repassa a confirmação", () => {
    expect(novoUsuarioSchema.parse(valido)).toEqual({ username: "maria.silva", email: "maria@empresa.com.br", senha: "Pet@2026Seguro", papel: "VENDAS" });
  });

  it("valida username, e-mail e papel", () => {
    const r = novoUsuarioSchema.safeParse({ ...valido, username: "ma ria", email: "x", papel: "DONO" });
    expect(r.error!.issues.map((i) => i.path[0])).toEqual(expect.arrayContaining(["username", "email", "papel"]));
  });

  it("aplica a política de senha e a confirmação", () => {
    expect(novoUsuarioSchema.safeParse({ ...valido, senha: "curta", confirmacaoSenha: "curta" }).success).toBe(false);
    const r = novoUsuarioSchema.safeParse({ ...valido, confirmacaoSenha: "Outra@2026x" });
    expect(r.error!.issues.map((i) => i.path[0])).toEqual(["confirmacaoSenha"]);
  });

  it("recusa senha igual ao username ou ao e-mail", () => {
    expect(violacaoSenha("Maria.Silva1!", "maria.silva1!", "x@y.com")).toContain("usuário");
    expect(violacaoSenha("Maria.2026!X", "m", "maria.2026!x@empresa.com")).toContain("e-mail");
    expect(violacaoSenha("Pet@2026Seguro", "maria", "maria@empresa.com")).toBeNull();
  });

  it("conta aceita só username e e-mail", () => {
    expect(contaSchema.parse({ username: " ana ", email: " ana@x.com ", papel: "ADMIN" })).toEqual({ username: "ana", email: "ana@x.com" });
  });
});
