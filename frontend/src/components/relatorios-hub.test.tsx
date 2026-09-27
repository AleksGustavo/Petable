// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { relatoriosDoPapel } from "@/lib/relatorios";

vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { RelatoriosHub } from "./relatorio-filtros";

describe("hub de relatórios", () => {
  it("ADMIN vê os 9 relatórios com o período do mês corrente", () => {
    render(<RelatoriosHub relatorios={relatoriosDoPapel("ADMIN")} hoje="2026-09-26" />);
    const links = screen.getAllByRole("link");
    expect(links).toHaveLength(9);
    expect(links[0].getAttribute("href")).toBe("/relatorios/vendas?dataInicio=2026-09-01&dataFim=2026-09-26");
    expect(screen.getByText("Margem estimada")).toBeTruthy();
  });

  it("VENDAS não vê custo, margem, despesas, caixas nem contas", () => {
    render(<RelatoriosHub relatorios={relatoriosDoPapel("VENDAS")} hoje="2026-09-26" />);
    expect(screen.getAllByRole("link")).toHaveLength(3);
    for (const titulo of [/Margem/, /Estoque/, /Despesas/, /Caixas/, /Contas/]) expect(screen.queryByText(titulo)).toBeNull();
  });

  it("sem relatórios mostra mensagem", () => {
    render(<RelatoriosHub relatorios={[]} hoje="2026-09-26" />);
    expect(screen.getByText(/Nenhum relatório/)).toBeTruthy();
  });
});
