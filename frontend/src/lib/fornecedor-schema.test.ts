import { describe, expect, it } from "vitest";
import { fornecedorSchema } from "@/lib/estoque";
import { cnpjValido, mascaraCnpj } from "@/lib/format";

const valido = {
  nomeFantasia: "Pet Distribuidora",
  razaoSocial: "Pet Distribuidora Ltda",
  cnpj: "11.222.333/0001-81",
  telefone: "(19) 3500-0000",
  email: "contato@pet.com.br",
  contato: "Carlos",
  observacoes: "",
  cep: "13610-000",
  logradouro: "Rua Exemplo",
  numero: "500",
  complemento: "",
  bairro: "Centro",
  cidade: "Leme",
  uf: "sp",
};

const erroEm = (dados: Record<string, string>, campo: string) =>
  fornecedorSchema.safeParse(dados).error?.issues.find((i) => i.path[0] === campo)?.message;

describe("fornecedorSchema", () => {
  it("aceita fornecedor válido e monta o endereço", () => {
    const r = fornecedorSchema.parse(valido);
    expect(r.cnpj).toBe("11222333000181");
    expect(r.telefone).toBe("1935000000");
    expect(r.endereco).toEqual({ cep: "13610000", logradouro: "Rua Exemplo", numero: "500", complemento: null, bairro: "Centro", cidade: "Leme", uf: "SP" });
  });

  it("rejeita CNPJ inválido", () => {
    expect(erroEm({ ...valido, cnpj: "11.222.333/0001-80" }, "cnpj")).toBe("CNPJ inválido");
    expect(erroEm({ ...valido, cnpj: "11111111111111" }, "cnpj")).toBe("CNPJ inválido");
    expect(cnpjValido("11222333000181")).toBe(true);
    expect(mascaraCnpj("11222333000181")).toBe("11.222.333/0001-81");
  });

  it("rejeita e-mail e endereço inválidos", () => {
    expect(erroEm({ ...valido, email: "sem-arroba" }, "email")).toBe("E-mail inválido");
    expect(erroEm({ ...valido, cep: "136" }, "cep")).toBe("CEP inválido");
    expect(erroEm({ ...valido, cidade: "" }, "cidade")).toBe("Informe a cidade");
    expect(erroEm({ ...valido, uf: "S" }, "uf")).toBe("UF inválida");
  });
});
