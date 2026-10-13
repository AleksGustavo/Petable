// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { RelatorioFiltros } from "./relatorio-filtros";

describe("filtros do relatório", () => {
  it("formulário GET com as datas e atalho ativo marcado", () => {
    render(<RelatorioFiltros tipo="vendas" dataInicio="2026-09-01" dataFim="2026-09-26" hoje="2026-09-26" />);
    expect((screen.getByLabelText("De") as HTMLInputElement).value).toBe("2026-09-01");
    expect((screen.getByLabelText("Até") as HTMLInputElement).value).toBe("2026-09-26");
    expect(screen.getByRole("link", { name: "Mês" }).getAttribute("aria-current")).toBe("true");
    expect(screen.getByRole("link", { name: "7 dias" }).getAttribute("href")).toBe("/relatorios/vendas?dataInicio=2026-09-20&dataFim=2026-09-26");
  });

  it("CSV passa pelo BFF e impressão abre a página sem AppShell", () => {
    render(<RelatorioFiltros tipo="margem" dataInicio="2026-09-01" dataFim="2026-09-26" hoje="2026-09-26" />);
    expect(screen.getByRole("link", { name: "Exportar CSV" }).getAttribute("href")).toBe("/api/relatorios/margem/csv?dataInicio=2026-09-01&dataFim=2026-09-26");
    expect(screen.getByRole("link", { name: "Imprimir" }).getAttribute("href")).toBe("/relatorios/margem/imprimir?dataInicio=2026-09-01&dataFim=2026-09-26");
  });

  it("período inválido mostra o erro e esconde exportação", () => {
    render(<RelatorioFiltros tipo="vendas" dataInicio="2026-09-26" dataFim="2026-09-01" hoje="2026-09-26" erro="A data final não pode ser anterior à inicial." />);
    expect(screen.getByRole("alert").textContent).toContain("anterior");
    expect(screen.queryByRole("link", { name: "Exportar CSV" })).toBeNull();
  });
});
