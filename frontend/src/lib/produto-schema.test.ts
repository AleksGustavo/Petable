import { describe, expect, it } from "vitest";
import { produtoAtualizacaoSchema, produtoSchema } from "@/lib/estoque";

const valido = {
  nome: " Ração Premium 10kg ",
  descricao: "",
  categoriaId: "1",
  fornecedorPadraoId: "",
  codigoBarras: "7891234567890",
  precoCusto: "120,50",
  precoVenda: "169,90",
  estoqueMinimo: "5",
};

const erroEm = (dados: Record<string, string>, campo: string) => {
  const r = produtoSchema.safeParse(dados);
  expect(r.success).toBe(false);
  return r.error?.issues.find((i) => i.path[0] === campo)?.message;
};

describe("produtoSchema", () => {
  it("aceita produto válido e converte valores", () => {
    expect(produtoSchema.parse(valido)).toEqual({
      nome: "Ração Premium 10kg",
      descricao: null,
      categoriaId: 1,
      fornecedorPadraoId: null,
      codigoBarras: "7891234567890",
      precoCusto: 120.5,
      precoVenda: 169.9,
      estoqueMinimo: 5,
    });
  });

  it("código de barras vazio vira null; inválido é rejeitado", () => {
    expect(produtoSchema.parse({ ...valido, codigoBarras: "  " }).codigoBarras).toBeNull();
    expect(erroEm({ ...valido, codigoBarras: "1234567" }, "codigoBarras")).toBe("O código de barras deve ter de 8 a 14 dígitos");
    expect(erroEm({ ...valido, codigoBarras: "78912345ABC" }, "codigoBarras")).toBeDefined();
    expect(erroEm({ ...valido, codigoBarras: "789123456789012" }, "codigoBarras")).toBeDefined();
  });

  it("rejeita preço de venda zero ou ausente", () => {
    expect(erroEm({ ...valido, precoVenda: "0,00" }, "precoVenda")).toBe("O preço de venda deve ser maior que zero");
    expect(erroEm({ ...valido, precoVenda: "" }, "precoVenda")).toBe("Informe o preço de venda");
    expect(erroEm({ ...valido, precoVenda: "10,999" }, "precoVenda")).toBe("Informe um preço válido");
  });

  it("rejeita estoque mínimo negativo e custo negativo", () => {
    expect(erroEm({ ...valido, estoqueMinimo: "-1" }, "estoqueMinimo")).toBe("O estoque mínimo não pode ser negativo");
    expect(erroEm({ ...valido, precoCusto: "-5" }, "precoCusto")).toBeDefined();
  });

  it("nunca envia saldo nem ativo; a atualização não envia custo", () => {
    const criado = produtoSchema.parse({ ...valido, quantidadeEstoque: "500", ativo: "false" });
    expect(criado).not.toHaveProperty("quantidadeEstoque");
    expect(criado).not.toHaveProperty("ativo");
    const atualizado = produtoAtualizacaoSchema.parse({ ...valido, quantidadeEstoque: "500" });
    expect(atualizado).not.toHaveProperty("quantidadeEstoque");
    expect(atualizado).not.toHaveProperty("precoCusto");
  });
});
