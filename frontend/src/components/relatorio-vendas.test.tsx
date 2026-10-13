// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { RelVendas } from "@/lib/relatorios";
import { ConteudoRelatorio } from "./relatorio-conteudo";

const dados: RelVendas = {
  dataInicio: "2026-09-01",
  dataFim: "2026-09-02",
  quantidade: 3,
  bruto: 330,
  descontos: 30,
  liquido: 300,
  ticketMedio: 100,
  canceladas: { quantidade: 1, valor: 50 },
  porDia: [
    { data: "2026-09-01", quantidade: 3, liquido: 300 },
    { data: "2026-09-02", quantidade: 0, liquido: 0 },
  ],
  porForma: [{ chave: "PIX", quantidade: 2, liquido: 200 }, { chave: "DINHEIRO", quantidade: 1, liquido: 100 }],
  porOperador: [{ chave: "ana", quantidade: 3, liquido: 300 }],
};

const texto = (s: string) => s.replace(/\u00a0/g, " ");

describe("relatório de vendas", () => {
  it("indicadores com líquido, ticket e canceladas separadas", () => {
    render(<ConteudoRelatorio relatorio={{ tipo: "vendas", dados }} />);
    const corpo = texto(document.body.textContent ?? "");
    expect(corpo).toContain("Faturamento líquido");
    expect(corpo).toContain("R$ 300,00");
    expect(corpo).toContain("R$ 100,00");
    expect(corpo).toMatch(/Canceladas \(fora do faturamento\): 1 \(R\$ 50,00\)/);
  });

  it("dia sem venda aparece zerado e formas usam rótulo", () => {
    render(<ConteudoRelatorio relatorio={{ tipo: "vendas", dados }} />);
    expect(screen.getAllByText("02/09/2026").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Pix").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Dinheiro").length).toBeGreaterThan(0);
    expect(screen.getAllByTestId("barra")).toHaveLength(2);
  });

  it("período sem vendas mostra estado vazio nas tabelas de grupos", () => {
    render(<ConteudoRelatorio relatorio={{ tipo: "vendas", dados: { ...dados, quantidade: 0, porDia: [], porForma: [], porOperador: [] } }} />);
    expect(screen.getAllByText("Sem dados no período.")).toHaveLength(3);
  });
});
