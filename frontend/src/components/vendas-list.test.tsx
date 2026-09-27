// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { Venda } from "@/lib/vendas";

vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { VendasFiltros, VendasLista } from "./vendas-lista";

const concluida: Venda = {
  id: 91,
  dataVenda: "2026-09-26T11:03:11.12",
  status: "CONCLUIDA",
  cliente: { id: 3, nome: "Maria Silva" },
  funcionario: null,
  operador: { id: 5, nome: "joana" },
  itens: null,
  quantidadeItens: -1,
  subtotal: 369.8,
  desconto: 5,
  valorTotal: 364.8,
  formaPagamento: "PIX",
  valorRecebido: null,
  troco: null,
  observacoes: null,
  canceladaEm: null,
  canceladaPor: null,
  motivoCancelamento: null,
};
const cancelada: Venda = { ...concluida, id: 92, status: "CANCELADA", cliente: null, formaPagamento: "DINHEIRO" };

describe("VendasLista", () => {
  it("renderiza vendas com data local, pagamento, total e links", () => {
    render(<VendasLista vendas={[concluida, cancelada]} />);
    expect(screen.getAllByText(/26\/09\/2026 11:03/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/Pix/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/364,80/).length).toBeGreaterThan(0);
    expect(screen.getAllByRole("link").map((l) => l.getAttribute("href"))).toEqual(expect.arrayContaining(["/vendas/91", "/vendas/92"]));
  });

  it("mostra os badges de status em texto", () => {
    render(<VendasLista vendas={[concluida, cancelada]} />);
    expect(screen.getAllByText("✓ Concluída").length).toBeGreaterThan(0);
    expect(screen.getAllByText("✕ Cancelada").length).toBeGreaterThan(0);
  });

  it("mostra estado vazio com e sem filtro", () => {
    const { rerender } = render(<VendasLista vendas={[]} />);
    expect(screen.getByText("Nenhuma venda registrada.")).toBeDefined();
    rerender(<VendasLista vendas={[]} filtrado />);
    expect(screen.getByText("Nenhuma venda encontrada com os filtros selecionados.")).toBeDefined();
  });

  it("filtros mantêm os valores atuais", () => {
    render(<VendasFiltros filtros={{ dataInicio: "2026-09-26", dataFim: "2026-09-26", status: "CANCELADA", formaPagamento: "PIX" }} />);
    expect(screen.getByLabelText("De")).toHaveProperty("value", "2026-09-26");
    expect(screen.getByLabelText("Status")).toHaveProperty("value", "CANCELADA");
    expect(screen.getByLabelText("Pagamento")).toHaveProperty("value", "PIX");
  });
});
