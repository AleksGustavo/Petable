// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Venda } from "@/lib/vendas";
import { Comprovante } from "./comprovante";

const venda: Venda = {
  id: 91,
  dataVenda: "2026-09-26T11:03:11",
  status: "CONCLUIDA",
  cliente: null,
  funcionario: null,
  operador: { id: 5, nome: "joana" },
  itens: [{ id: 1, produto: { id: 12, nome: "Ração Premium" }, quantidade: 2, precoUnitario: 169.9, subtotal: 339.8 }],
  quantidadeItens: 1,
  subtotal: 339.8,
  desconto: 5,
  valorTotal: 334.8,
  formaPagamento: "DINHEIRO",
  valorRecebido: 400,
  troco: 65.2,
  observacoes: null,
  canceladaEm: null,
  canceladaPor: null,
  motivoCancelamento: null,
};

afterEach(() => vi.restoreAllMocks());

describe("Comprovante", () => {
  it("mostra os dados obrigatórios e 'Comprovante não fiscal'", () => {
    render(<Comprovante venda={venda} />);
    expect(screen.getByText("Comprovante não fiscal")).toBeDefined();
    expect(screen.getByText(/Venda nº 91/)).toBeDefined();
    expect(screen.getByText(/26\/09\/2026 11:03/)).toBeDefined();
    expect(screen.getByText("Ração Premium")).toBeDefined();
    expect(screen.getByText(/334,80/)).toBeDefined();
    expect(screen.getByText("Dinheiro")).toBeDefined();
    expect(screen.getByText(/65,20/)).toBeDefined();
    expect(screen.getByText("Operador: joana")).toBeDefined();
    expect(screen.queryByText("CANCELADA")).toBeNull();
  });

  it("venda cancelada imprime CANCELADA em destaque", () => {
    render(<Comprovante venda={{ ...venda, status: "CANCELADA" }} />);
    expect(screen.getByText("CANCELADA")).toBeDefined();
  });

  it("botão imprime a página", async () => {
    const imprimir = vi.spyOn(window, "print").mockImplementation(() => {});
    render(<Comprovante venda={venda} />);
    await userEvent.setup().click(screen.getByRole("button", { name: "Imprimir" }));
    expect(imprimir).toHaveBeenCalled();
  });
});
