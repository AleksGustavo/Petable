import { describe, expect, it } from "vitest";
import { hojeISO } from "@/lib/datas";
import { compraSchema, errosPorCaminho, paraCentavos, subtotalCentavos, totalCentavos } from "@/lib/estoque";

const base = { fornecedorId: "4", funcionarioId: "", dataCompra: hojeISO(), formaPagamento: "Boleto", observacoes: "" };
const item = (produtoId: number, quantidade: string, precoCustoUnitario: string) => ({ produtoId, quantidade, precoCustoUnitario });

function erros(dados: Record<string, unknown>) {
  const r = compraSchema.safeParse(dados);
  expect(r.success).toBe(false);
  return r.success ? {} : errosPorCaminho(r.error);
}

describe("compraSchema", () => {
  it("aceita compra com um item e não envia total nem status", () => {
    const r = compraSchema.parse({ ...base, itens: [item(10, "5", "26,00")], valorTotal: "1", status: "RECEBIDO" });
    expect(r.itens).toEqual([{ produtoId: 10, quantidade: 5, precoCustoUnitario: 26 }]);
    expect(r.funcionarioId).toBeNull();
    expect(r).not.toHaveProperty("valorTotal");
    expect(r).not.toHaveProperty("status");
  });

  it("rejeita compra sem itens", () => {
    expect(erros({ ...base, itens: [] }).itens).toBe("Adicione pelo menos um item");
  });

  it("rejeita produto duplicado na linha repetida", () => {
    const e = erros({ ...base, itens: [item(10, "1", "1"), item(10, "2", "1")] });
    expect(e["itens.1.produtoId"]).toBe("Este produto já está na compra");
    expect(e["itens.0.produtoId"]).toBeUndefined();
  });

  it("rejeita quantidade e custo zero", () => {
    const e = erros({ ...base, itens: [item(10, "0", "0,00")] });
    expect(e["itens.0.quantidade"]).toBe("A quantidade deve ser de 1 a 1.000.000");
    expect(e["itens.0.precoCustoUnitario"]).toBe("O custo unitário deve ser maior que zero");
  });

  it("rejeita data futura", () => {
    expect(erros({ ...base, dataCompra: "2999-01-01", itens: [item(1, "1", "1")] }).dataCompra).toBe("A data da compra não pode ser futura");
  });

  it("calcula subtotal e total visual em centavos, sem erro de ponto flutuante", () => {
    expect(paraCentavos("1.234,56")).toBe(123456);
    expect(paraCentavos("0.1")).toBe(10);
    expect(subtotalCentavos("3", "30,33")).toBe(9099);
    // 3 × 0,10 = 0,30 exatos (em ponto flutuante daria 0.30000000000000004).
    expect(totalCentavos([item(1, "3", "0,10"), item(2, "10", "120,50")])).toBe(30 + 120500);
    expect(subtotalCentavos("", "abc")).toBe(0);
  });
});
