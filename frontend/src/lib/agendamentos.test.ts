import { describe, expect, it } from "vitest";
import { acoesPermitidas } from "./agendamentos";

describe("acoesPermitidas", () => {
  it("respeita estado e papel", () => {
    expect(acoesPermitidas("AGENDADO", "VENDAS", false)).toEqual(["CONFIRMADO", "CANCELADO"]);
    expect(acoesPermitidas("AGENDADO", "SERVICO", false)).toEqual([]);
    expect(acoesPermitidas("CONFIRMADO", "SERVICO", true)).toEqual(["EM_ANDAMENTO", "NAO_COMPARECEU"]);
    expect(acoesPermitidas("EM_ANDAMENTO", "SERVICO", true)).toEqual(["CONCLUIDO"]);
    expect(acoesPermitidas("CONCLUIDO", "ADMIN", true)).toEqual([]);
  });
});
