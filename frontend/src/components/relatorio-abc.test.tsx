// @vitest-environment jsdom
import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { RelCurvaAbc, RelMaisVendidos } from "@/lib/relatorios";
import { ConteudoRelatorio } from "./relatorio-conteudo";

const abc: RelCurvaAbc = {
  itens: [
    { produtoId: 1, nome: "Ração", faturamento: 800, participacao: 80, acumulado: 80, classe: "A" },
    { produtoId: 2, nome: "Coleira", faturamento: 150, participacao: 15, acumulado: 95, classe: "B" },
    { produtoId: 3, nome: "Petisco", faturamento: 50, participacao: 5, acumulado: 100, classe: "C" },
  ],
  resumo: [
    { classe: "A", produtos: 1, faturamento: 800, participacao: 80 },
    { classe: "B", produtos: 1, faturamento: 150, participacao: 15 },
    { classe: "C", produtos: 1, faturamento: 50, participacao: 5 },
  ],
  total: 1000,
};

describe("curva ABC e mais vendidos", () => {
  it("resumo por classe e classe de cada produto", () => {
    render(<ConteudoRelatorio relatorio={{ tipo: "curva-abc", dados: abc }} />);
    const tabela = screen.getByRole("table");
    const linhaColeira = within(tabela).getByText("Coleira").closest("tr") as HTMLElement;
    expect(within(linhaColeira).getByText("Classe B")).toBeTruthy();
    expect(within(linhaColeira).getByText("95,00%")).toBeTruthy();
    expect(screen.getAllByText("Classe A").length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText(/até 80% = A/)).toBeTruthy();
  });

  it("mais vendidos: ranking com participação e barras por quantidade", () => {
    const dados: RelMaisVendidos = {
      itens: [
        { produtoId: 1, nome: "Ração", categoria: "Alimentos", quantidade: 10, faturamento: 800, participacao: 80 },
        { produtoId: 2, nome: "Coleira", categoria: "Acessórios", quantidade: 5, faturamento: 200, participacao: 20 },
      ],
      total: 1000,
    };
    render(<ConteudoRelatorio relatorio={{ tipo: "produtos-mais-vendidos", dados }} />);
    expect(screen.getAllByTestId("barra").map((b) => b.getAttribute("data-largura"))).toEqual(["100", "50"]);
    expect(screen.getAllByText("80,00%").length).toBeGreaterThan(0);
  });
});
