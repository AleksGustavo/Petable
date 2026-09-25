import { describe, expect, it } from "vitest";
import { clienteSchema, errosDaApi } from "./clientes";

const base = { nome: "Maria", telefone: "(19) 99876-5432" };

describe("clienteSchema", () => {
  it("normaliza documentos e omite endereço vazio", () => {
    const r = clienteSchema.parse({ ...base, cpf: "529.982.247-25", email: "" });
    expect(r).toMatchObject({ cpf: "52998224725", telefone: "19998765432", email: null, endereco: null });
  });

  it("monta o endereço quando o CEP é informado", () => {
    const r = clienteSchema.parse({ ...base, cep: "13616-140", logradouro: "Rua A", cidade: "Leme", uf: "sp" });
    expect(r.endereco).toMatchObject({ cep: "13616140", uf: "SP" });
  });

  it("exige campos do endereço quando parte dele foi preenchida", () => {
    const r = clienteSchema.safeParse({ ...base, logradouro: "Rua A" });
    expect(r.success).toBe(false);
    const campos = r.error!.issues.map((i) => i.path[0]);
    expect(campos).toEqual(expect.arrayContaining(["cep", "cidade", "uf"]));
  });

  it("recusa CPF e telefone inválidos", () => {
    const r = clienteSchema.safeParse({ nome: "X", cpf: "123", telefone: "9999" });
    expect(r.success).toBe(false);
  });

  it("traduz erros da API para nomes de campo", () => {
    expect(errosDaApi({ "endereco.cep": "CEP inválido", cpf: "CPF inválido" })).toEqual({ cep: "CEP inválido", cpf: "CPF inválido" });
  });
});
