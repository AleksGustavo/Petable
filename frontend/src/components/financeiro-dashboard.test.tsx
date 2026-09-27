// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { DashboardFinanceiro, MeuCaixa } from "./dashboard-financeiro";

describe("dashboard financeiro", () => {
  it("ADMIN vê saldo, contas vencidas, a vencer e caixas abertos", () => {
    render(
      <DashboardFinanceiro
        resumo={{ data: "2026-09-26", entradas: 3580, saidas: 980, saldo: 2600, contasVencidas: 2, valorContasVencidas: 1430, contasAVencer7Dias: 4, valorAVencer7Dias: 2870, caixasAbertos: 2 }}
      />,
    );
    expect(screen.getByText("Saldo de hoje")).toBeDefined();
    expect(screen.getByText(/2\.600,00/)).toBeDefined();
    expect(screen.getByText("Contas vencidas")).toBeDefined();
    expect(screen.getByText(/Pagar agora/)).toBeDefined();
    expect(screen.getByText("A vencer em 7 dias")).toBeDefined();
    expect(screen.getByText("Caixas abertos")).toBeDefined();
    expect(screen.getAllByRole("link").map((l) => l.getAttribute("href"))).toContain("/financeiro/contas-a-pagar?situacao=VENCIDA");
  });

  it("meu caixa aberto mostra desde quando e o dinheiro esperado", () => {
    render(<MeuCaixa resumo={{ aberto: true, caixaId: 18, abertoEm: "2026-09-26T08:02:14", saldoEsperadoDinheiro: 780 }} />);
    expect(screen.getByText(/Aberto desde 26\/09\/2026 08:02/)).toBeDefined();
    expect(screen.getByText(/780,00/)).toBeDefined();
  });

  it("meu caixa fechado oferece abrir", () => {
    render(<MeuCaixa resumo={{ aberto: false, caixaId: null, abertoEm: null, saldoEsperadoDinheiro: null }} />);
    expect(screen.getByText("○ Caixa fechado")).toBeDefined();
    expect(screen.getByRole("link").getAttribute("href")).toBe("/caixa");
  });
});
