// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Compra } from "@/lib/estoque";

const { receberCompra, cancelarCompra } = vi.hoisted(() => ({
  receberCompra: vi.fn(async () => ({})),
  cancelarCompra: vi.fn(async () => ({})),
}));
vi.mock("@/app/actions/estoque", () => ({ receberCompra, cancelarCompra }));
vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { CompraDetalhes } from "./compra-detalhes";

const pedido: Compra = {
  id: 50,
  fornecedor: { id: 4, nome: "Pet Distribuidora" },
  funcionario: null,
  dataCompra: "2026-09-20",
  dataEntrega: null,
  status: "PEDIDO",
  formaPagamento: "Boleto",
  observacoes: null,
  valorTotal: 435,
  quantidadeItens: 2,
  itens: [
    { id: 1, produto: { id: 10, nome: "Ração Premium" }, quantidade: 5, precoCustoUnitario: 26, subtotal: 130 },
    { id: 2, produto: { id: 12, nome: "Coleira" }, quantidade: 10, precoCustoUnitario: 30.5, subtotal: 305 },
  ],
  createdAt: "",
};

afterEach(() => {
  vi.restoreAllMocks();
  receberCompra.mockClear();
  cancelarCompra.mockClear();
});

describe("CompraDetalhes", () => {
  it("mostra itens e o total vindo da API", () => {
    render(<CompraDetalhes compra={pedido} podeAdministrar={false} hoje="2026-09-25" />);
    expect(screen.getAllByText("Ração Premium").length).toBeGreaterThan(0);
    expect(screen.getAllByText(/305,00/).length).toBeGreaterThan(0);
    expect(screen.getByTestId("total-compra").textContent).toMatch(/435,00/);
    expect(screen.getByText("Pedido")).toBeDefined();
  });

  it("PEDIDO com ADMIN mostra receber e cancelar; sem permissão, nenhuma ação", () => {
    const { rerender } = render(<CompraDetalhes compra={pedido} podeAdministrar hoje="2026-09-25" />);
    expect(screen.getByRole("button", { name: "Receber compra" })).toBeDefined();
    expect(screen.getByRole("button", { name: "Cancelar compra" })).toBeDefined();
    rerender(<CompraDetalhes compra={pedido} podeAdministrar={false} hoje="2026-09-25" />);
    expect(screen.queryByRole("button", { name: "Receber compra" })).toBeNull();
  });

  it("compra recebida ou cancelada não oferece nenhuma ação", () => {
    const { rerender } = render(<CompraDetalhes compra={{ ...pedido, status: "RECEBIDO", dataEntrega: "2026-09-25" }} podeAdministrar hoje="2026-09-25" />);
    expect(screen.queryByRole("button")).toBeNull();
    expect(screen.getByText("Recebido")).toBeDefined();
    rerender(<CompraDetalhes compra={{ ...pedido, status: "CANCELADO" }} podeAdministrar hoje="2026-09-25" />);
    expect(screen.queryByRole("button")).toBeNull();
    expect(screen.getByText("Cancelado")).toBeDefined();
  });

  it("receber e cancelar pedem confirmação", async () => {
    const user = userEvent.setup();
    const confirmar = vi.spyOn(window, "confirm").mockReturnValue(false);
    render(<CompraDetalhes compra={pedido} podeAdministrar hoje="2026-09-25" />);

    await user.click(screen.getByRole("button", { name: "Receber compra" }));
    expect(confirmar).toHaveBeenLastCalledWith(expect.stringContaining("Registrar o recebimento em 25/09/2026"));

    await user.type(screen.getByLabelText(/Motivo/), "Fornecedor sem estoque");
    await user.click(screen.getByRole("button", { name: "Cancelar compra" }));
    expect(confirmar).toHaveBeenLastCalledWith(expect.stringContaining("Cancelar a compra #50"));

    expect(receberCompra).not.toHaveBeenCalled();
    expect(cancelarCompra).not.toHaveBeenCalled();
  });
});
