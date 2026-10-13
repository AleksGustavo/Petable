import { describe, expect, it } from "vitest";
import { descontoCentavos, trocoCentavos, vendaSchema, type VendaEntrada } from "@/lib/vendas";

const valida: VendaEntrada = {
  clienteId: null,
  itens: [{ produtoId: 10, quantidade: 2 }],
  descontoCentavos: 500,
  formaPagamento: "PIX",
  valorRecebidoCentavos: null,
  observacoes: "  ",
};

const erro = (entrada: VendaEntrada) => {
  const r = vendaSchema.safeParse(entrada);
  expect(r.success).toBe(false);
  return r.error?.issues[0]?.message;
};

describe("vendaSchema", () => {
  it("aceita venda válida e monta o payload sem preço, total nem status", () => {
    const r = vendaSchema.parse({ ...valida, precoUnitario: 1, valorTotal: 1 } as VendaEntrada);
    expect(r).toEqual({ clienteId: null, itens: [{ produtoId: 10, quantidade: 2 }], desconto: 5, formaPagamento: "PIX", valorRecebido: null, observacoes: null });
  });

  it("rejeita venda sem itens", () => {
    expect(erro({ ...valida, itens: [] })).toBe("Adicione pelo menos um item");
  });

  it("rejeita quantidade zero", () => {
    expect(erro({ ...valida, itens: [{ produtoId: 10, quantidade: 0 }] })).toBe("A quantidade deve ser de 1 a 10.000");
  });

  it("converte desconto em % para valor em centavos", () => {
    expect(descontoCentavos("percentual", "10", 36980)).toBe(3698);
    expect(descontoCentavos("percentual", "2,5", 10000)).toBe(250);
    expect(descontoCentavos("valor", "5,00", 10000)).toBe(500);
    expect(descontoCentavos("percentual", "abc", 10000)).toBeNaN();
    expect(descontoCentavos("valor", "", 10000)).toBe(0);
  });

  it("exige valor recebido em dinheiro e ignora nas demais formas", () => {
    const r = vendaSchema.safeParse({ ...valida, formaPagamento: "DINHEIRO", valorRecebidoCentavos: null });
    expect(r.success).toBe(false);
    expect(r.error?.issues.find((i) => i.path[0] === "valorRecebido")?.message).toBe("Informe o valor recebido.");
    expect(vendaSchema.parse({ ...valida, formaPagamento: "CARTAO_DEBITO", valorRecebidoCentavos: 99900 }).valorRecebido).toBeNull();
    expect(vendaSchema.parse({ ...valida, formaPagamento: "DINHEIRO", valorRecebidoCentavos: 40000 }).valorRecebido).toBe(400);
  });

  it("calcula troco em centavos sem erro de ponto flutuante", () => {
    expect(trocoCentavos(40000, 36480)).toBe(3520);
    expect(trocoCentavos(30, 10)).toBe(20);
    expect(trocoCentavos(null, 100)).toBeNull();
  });
});
