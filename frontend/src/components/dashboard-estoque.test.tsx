// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { DashboardEstoque } from "./dashboard-estoque";

describe("DashboardEstoque", () => {
  it("mostra o contador e leva à lista filtrada", () => {
    render(<DashboardEstoque produtosAbaixoDoMinimo={7} />);
    expect(screen.getByText("Produtos abaixo do estoque mínimo")).toBeDefined();
    expect(screen.getByText("7")).toBeDefined();
    expect(screen.getByText(/Ver produtos para repor/)).toBeDefined();
    expect(screen.getByRole("link").getAttribute("href")).toBe("/produtos?abaixoMinimo=true&ativo=true");
  });

  it("zero produtos indica estoque em dia", () => {
    render(<DashboardEstoque produtosAbaixoDoMinimo={0} />);
    expect(screen.getByText("0")).toBeDefined();
    expect(screen.getByText(/Estoque em dia/)).toBeDefined();
  });
});
