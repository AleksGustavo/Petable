// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { ContaPagar } from "@/lib/financeiro";

vi.mock("@/app/actions/financeiro", () => ({ pagarConta: vi.fn() }));
vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { ContasFiltros, ContasLista } from "./contas-pagar";

const conta: ContaPagar = {
  id: 40,
  compraId: 12,
  fornecedor: { id: 4, nome: "Pet Distribuidora" },
  descricao: "Compra #12",
  valorOriginal: 1250,
  saldo: 1250,
  dataVencimento: "2026-10-10",
  status: "ABERTA",
  vencida: false,
  situacao: "A_VENCER",
  pagaEm: null,
  formaPagamento: null,
  usuarioPagamento: null,
  lancamentoId: null,
};

describe("contas a pagar", () => {
  it("lista contas com situação em texto (não só cor)", () => {
    render(
      <ContasLista
        contas={[conta, { ...conta, id: 41, situacao: "VENCIDA", vencida: true }, { ...conta, id: 42, situacao: "VENCE_HOJE" }, { ...conta, id: 43, status: "PAGA", situacao: "PAGA", saldo: 0 }]}
      />,
    );
    expect(screen.getAllByText("A vencer").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Vencida").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Vence hoje").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Paga").length).toBeGreaterThan(0);
    expect(screen.getAllByText(/1\.250,00/).length).toBeGreaterThan(0);
    expect(screen.getAllByRole("link").map((l) => l.getAttribute("href"))).toContain("/financeiro/contas-a-pagar/41");
  });

  it("mostra estado vazio com e sem filtro", () => {
    const { rerender } = render(<ContasLista contas={[]} />);
    expect(screen.getByText(/Nenhuma conta a pagar/)).toBeDefined();
    rerender(<ContasLista contas={[]} filtrado />);
    expect(screen.getByText("Nenhuma conta nesta situação.")).toBeDefined();
  });

  it("filtro marca a situação atual", () => {
    render(<ContasFiltros situacao="VENCIDA" />);
    expect(screen.getByRole("link", { name: "Vencida" }).getAttribute("aria-current")).toBe("page");
    expect(screen.getByRole("link", { name: "Todas" }).getAttribute("href")).toBe("/financeiro/contas-a-pagar");
  });
});
