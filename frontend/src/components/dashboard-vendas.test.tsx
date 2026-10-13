// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { DashboardVendas } from "./dashboard-vendas";

describe("DashboardVendas", () => {
  it("mostra quantidade e total das vendas do dia", () => {
    render(<DashboardVendas resumo={{ data: "2026-09-26", quantidade: 14, total: 2350.4 }} />);
    expect(screen.getByText("Vendas hoje")).toBeDefined();
    expect(screen.getByText("14")).toBeDefined();
    expect(screen.getByTestId("total-vendas-dia").textContent).toMatch(/2\.350,40/);
    expect(screen.getByRole("link").getAttribute("href")).toBe("/vendas");
  });

  it("dia sem vendas mostra zero", () => {
    render(<DashboardVendas resumo={{ data: "2026-09-26", quantidade: 0, total: 0 }} />);
    expect(screen.getByText("0")).toBeDefined();
    expect(screen.getByTestId("total-vendas-dia").textContent).toMatch(/0,00/);
  });
});
