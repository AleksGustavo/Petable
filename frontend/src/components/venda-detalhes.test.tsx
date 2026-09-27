// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Venda } from "@/lib/vendas";

const { cancelarVenda } = vi.hoisted(() => ({ cancelarVenda: vi.fn(async () => ({})) }));
vi.mock("@/app/actions/vendas", () => ({ cancelarVenda }));
vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { VendaDetalhes } from "./venda-detalhes";

const venda: Venda = {
  id: 91,
  dataVenda: "2026-09-26T11:03:11",
  status: "CONCLUIDA",
  cliente: { id: 3, nome: "Maria Silva" },
  funcionario: null,
  operador: { id: 5, nome: "joana" },
  itens: [
    { id: 1, produto: { id: 12, nome: "Ração Premium" }, quantidade: 2, precoUnitario: 169.9, subtotal: 339.8 },
    { id: 2, produto: { id: 10, nome: "Coleira" }, quantidade: 1, precoUnitario: 30, subtotal: 30 },
  ],
  quantidadeItens: 2,
  subtotal: 369.8,
  desconto: 5,
  valorTotal: 364.8,
  formaPagamento: "DINHEIRO",
  valorRecebido: 400,
  troco: 35.2,
  observacoes: null,
  canceladaEm: null,
  canceladaPor: null,
  motivoCancelamento: null,
};

afterEach(() => {
  vi.restoreAllMocks();
  cancelarVenda.mockClear();
});

describe("VendaDetalhes", () => {
  it("mostra itens, totais, recebido e troco", () => {
    render(<VendaDetalhes venda={venda} podeCancelar={false} />);
    expect(screen.getByText("Ração Premium")).toBeDefined();
    expect(screen.getByText(/339,80/)).toBeDefined();
    expect(screen.getByTestId("total-venda").textContent).toMatch(/364,80/);
    expect(screen.getByText(/35,20/)).toBeDefined();
    expect(screen.getByRole("link", { name: /Comprovante/ }).getAttribute("href")).toBe("/vendas/91/comprovante");
  });

  it("cancelar só aparece para quem pode cancelar venda concluída", () => {
    const { rerender } = render(<VendaDetalhes venda={venda} podeCancelar={false} />);
    expect(screen.queryByRole("button", { name: "Cancelar venda" })).toBeNull();
    rerender(<VendaDetalhes venda={venda} podeCancelar />);
    expect(screen.getByRole("button", { name: "Cancelar venda" })).toBeDefined();
  });

  it("pede confirmação avisando o estorno e não envia se cancelar", async () => {
    const user = userEvent.setup();
    const confirmar = vi.spyOn(window, "confirm").mockReturnValue(false);
    render(<VendaDetalhes venda={venda} podeCancelar />);

    await user.type(screen.getByLabelText(/Motivo/), "Cliente desistiu");
    await user.click(screen.getByRole("button", { name: "Cancelar venda" }));

    expect(confirmar).toHaveBeenCalledWith(expect.stringContaining("3 unidade(s) voltarão ao estoque"));
    expect(cancelarVenda).not.toHaveBeenCalled();
  });

  it("venda cancelada mostra quem, quando e o motivo, sem ações", () => {
    render(
      <VendaDetalhes
        venda={{ ...venda, status: "CANCELADA", canceladaEm: "2026-09-26T12:00:00", canceladaPor: { id: 1, nome: "admin" }, motivoCancelamento: "Cliente desistiu" }}
        podeCancelar
      />,
    );
    expect(screen.getByText(/Cancelada em 26\/09\/2026 12:00 por admin\. Motivo: Cliente desistiu/)).toBeDefined();
    expect(screen.queryByRole("button")).toBeNull();
  });
});
