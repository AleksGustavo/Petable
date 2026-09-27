import { describe, expect, it } from "vitest";
import { funcionarioSchema, meuFuncionarioSchema } from "./usuarios";

const valido = { nome: " Ana Souza ", cpf: "529.982.247-25", dataNascimento: "1995-04-20", sexo: "FEMININO", telefone: "(19) 99999-0000" };

describe("funcionário", () => {
  it("normaliza CPF, telefone e omite endereço vazio", () => {
    expect(funcionarioSchema.parse(valido)).toMatchObject({ nome: "Ana Souza", cpf: "52998224725", telefone: "19999990000", endereco: null });
  });

  it("recusa CPF inválido, nascimento futuro e telefone incompleto", () => {
    const r = funcionarioSchema.safeParse({ ...valido, cpf: "123.456.789-00", dataNascimento: "2999-01-01", telefone: "9999" });
    expect(r.error!.issues.map((i) => i.path[0])).toEqual(expect.arrayContaining(["cpf", "dataNascimento", "telefone"]));
  });

  it("exige o endereço completo quando parte foi preenchida", () => {
    const r = funcionarioSchema.safeParse({ ...valido, logradouro: "Rua A" });
    expect(r.error!.issues.map((i) => i.path[0])).toEqual(expect.arrayContaining(["cep", "cidade", "uf"]));
  });

  it("perfil do próprio funcionário não envia CPF", () => {
    const r = meuFuncionarioSchema.parse({ ...valido, cep: "13610-000", logradouro: "Rua A", cidade: "Leme", uf: "sp" });
    expect(r).not.toHaveProperty("cpf");
    expect(r.endereco).toMatchObject({ cep: "13610000", uf: "SP" });
    expect(r.sexo).toBe("FEMININO");
  });
});
