// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { RelEstoque, RelMargem } from "@/lib/relatorios";
import { ConteudoRelatorio } from "./relatorio-conteudo";

const margem: RelMargem = {
  receita: 100,
  custoEstimado: 120,
  margem: -20,
  margemPercentual: -20,
  produtosSemCusto: 2,
  porProduto: [{ produtoId: 1, nome: "Ração", quantidade: 2, receita: 100, custoEstimado: 120, margem: -20, margemPercentual: -20 }],
  aviso: "Custo estimado pelo custo médio ATUAL do produto.",
};

describe("margem e estoque (custo estimado)", () => {
  it("aviso do custo atual em destaque e produtos sem custo", () => {
    render(<ConteudoRelatorio relatorio={{ tipo: "margem", dados: margem }} />);
    const alerta = screen.getByRole("alert");
    expect(alerta.textContent).toContain("custo médio ATUAL");
    expect(alerta.textContent).toContain("2 produto(s) sem custo");
  });

  it("margem negativa aparece em vermelho", () => {
    render(<ConteudoRelatorio relatorio={{ tipo: "margem", dados: margem }} />);
    const valor = screen.getByText("Margem estimada").nextElementSibling as HTMLElement;
    expect(valor.className).toContain("text-danger");
    expect(valor.textContent?.replace(/\u00a0/g, " ")).toBe("-R$ 20,00");
  });

  it("estoque: giro e cobertura nulos viram traço", () => {
    const estoque: RelEstoque = {
      itens: [{ produtoId: 1, nome: "Coleira", saldoAtual: 4, custoMedio: 10, valorEstoque: 40, quantidadeVendida: 0, giro: null, coberturaDias: null }],
      valorTotalEstoque: 40,
      produtosSemCusto: 0,
      aviso: "Valor pelo custo médio atual.",
    };
    render(<ConteudoRelatorio relatorio={{ tipo: "estoque", dados: estoque }} />);
    expect(screen.getByRole("status").textContent).toContain("custo médio atual");
    expect(screen.getAllByText("—").length).toBeGreaterThanOrEqual(2);
  });
});
