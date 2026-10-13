// @vitest-environment jsdom
import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { BarrasHorizontais, TabelaResponsiva } from "./relatorio-ui";

describe("gráfico de barras em CSS", () => {
  it("largura proporcional ao maior valor e valor em texto", () => {
    render(
      <BarrasHorizontais
        titulo="Faturamento"
        itens={[{ rotulo: "A", valor: 200 }, { rotulo: "B", valor: 50 }, { rotulo: "C", valor: 0 }]}
        formatar={(v) => `R$ ${v}`}
      />,
    );
    const larguras = screen.getAllByTestId("barra").map((b) => b.getAttribute("data-largura"));
    expect(larguras).toEqual(["100", "25", "0"]);
    expect(screen.getByText("R$ 50")).toBeTruthy();
    expect(screen.getByRole("figure", { name: "Faturamento" })).toBeTruthy();
  });

  it("todos zerados não dividem por zero e lista vazia não renderiza", () => {
    const { container, rerender } = render(<BarrasHorizontais titulo="X" itens={[{ rotulo: "A", valor: 0 }]} formatar={String} />);
    expect(screen.getByTestId("barra").getAttribute("data-largura")).toBe("0");
    rerender(<BarrasHorizontais titulo="X" itens={[]} formatar={String} />);
    expect(container.innerHTML).toBe("");
  });

  it("valor negativo usa a cor de perigo", () => {
    render(<BarrasHorizontais titulo="X" itens={[{ rotulo: "Falta", valor: -10 }]} formatar={String} />);
    expect(screen.getByTestId("barra").className).toContain("bg-danger");
  });

  it("tabela responsiva: mesma informação em cartões e tabela; vazio com mensagem", () => {
    const { rerender } = render(
      <TabelaResponsiva titulo="Por dia" colunas={[{ titulo: "Dia" }, { titulo: "Valor", direita: true }]} linhas={[{ chave: 1, celulas: ["01/09", "R$ 10"] }]} />,
    );
    const secao = screen.getByRole("region", { name: "Por dia" });
    expect(within(secao).getAllByText("R$ 10")).toHaveLength(2);
    expect(within(secao).getByRole("columnheader", { name: "Valor" })).toBeTruthy();
    rerender(<TabelaResponsiva titulo="Por dia" colunas={[{ titulo: "Dia" }]} linhas={[]} />);
    expect(screen.getByText("Sem dados no período.")).toBeTruthy();
  });
});
