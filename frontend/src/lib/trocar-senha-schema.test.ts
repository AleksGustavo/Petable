import { describe, expect, it } from "vitest";
import { redefinirSenhaSchema, trocarSenhaSchema } from "./usuarios";

const schema = trocarSenhaSchema("ana", "ana@empresa.com");

describe("troca e redefinição de senha", () => {
  it("aceita troca válida", () => {
    expect(schema.safeParse({ senhaAtual: "Senha@Atual2026", novaSenha: "Nova@Senha2026", confirmacaoNovaSenha: "Nova@Senha2026" }).success).toBe(true);
  });

  it("exige nova diferente da atual e confirmação igual", () => {
    const igual = schema.safeParse({ senhaAtual: "Nova@Senha2026", novaSenha: "Nova@Senha2026", confirmacaoNovaSenha: "Nova@Senha2026" });
    expect(igual.error!.issues[0].message).toContain("diferente da atual");
    const conf = schema.safeParse({ senhaAtual: "x", novaSenha: "Nova@Senha2026", confirmacaoNovaSenha: "Outra@Senha2026" });
    expect(conf.error!.issues.map((i) => i.path[0])).toEqual(["confirmacaoNovaSenha"]);
  });

  it("aplica a política na nova senha", () => {
    const r = schema.safeParse({ senhaAtual: "x", novaSenha: "semnumero!!", confirmacaoNovaSenha: "semnumero!!" });
    expect(r.error!.issues.map((i) => i.path[0])).toContain("novaSenha");
  });

  it("redefinição exige token", () => {
    const r = redefinirSenhaSchema.safeParse({ token: "", novaSenha: "Nova@Senha2026", confirmacaoNovaSenha: "Nova@Senha2026" });
    expect(r.error!.issues.map((i) => i.path[0])).toEqual(["token"]);
  });
});
