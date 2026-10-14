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

const comLote: Venda = {
  id: 91,
  dataVenda: "2026-09-26T11:03:11",
  status: "CONCLUIDA",
  cliente: null,
  funcionario: null,
  operador: { id: 5, nome: "joana" },
  itens: [
    {
      id: 1,
      produto: { id: 12, nome: "Vacina X" },
      quantidade: 5,
      precoUnitario: 80,
      subtotal: 400,
      lotes: [
        { loteId: 1, codigoLote: "A", validade: "2026-12-01", quantidade: 3 },
        { loteId: 2, codigoLote: "B", validade: null, quantidade: 2 },
      ],
    },
  ],
  quantidadeItens: 1,
  subtotal: 400,
  desconto: 0,
  valorTotal: 400,
  formaPagamento: "PIX",
  valorRecebido: null,
  troco: null,
  observacoes: null,
  canceladaEm: null,
  canceladaPor: null,
  motivoCancelamento: null,
};

afterEach(() => {
  vi.restoreAllMocks();
  cancelarVenda.mockClear();
});

describe("venda com lote", () => {
  it("mostra de quais lotes cada item saiu", () => {
    render(<VendaDetalhes venda={comLote} podeCancelar />);
    expect(screen.getByText(/A \(val\. 01\/12\/2026\) × 3 · B × 2/)).toBeDefined();
  });

  it("cancelar exige escolher o destino: nenhuma opção vem marcada e o envio é bloqueado", async () => {
    const user = userEvent.setup();
    const confirmar = vi.spyOn(window, "confirm").mockReturnValue(true);
    render(<VendaDetalhes venda={comLote} podeCancelar />);

    const retornar = screen.getByRole("radio", { name: /Voltar ao estoque/ }) as HTMLInputElement;
    const descartar = screen.getByRole("radio", { name: /Descartar \(perda\)/ }) as HTMLInputElement;
    expect(retornar.checked).toBe(false);
    expect(descartar.checked).toBe(false);

    await user.type(screen.getByLabelText(/Motivo/), "Cliente devolveu o produto");
    await user.click(screen.getByRole("button", { name: "Cancelar venda" }));
    expect(await screen.findByText("Escolha o que fazer com o produto devolvido.")).toBeDefined();
    expect(confirmar).not.toHaveBeenCalled();
    expect(cancelarVenda).not.toHaveBeenCalled();
  });

  it("a confirmação diz o que vai acontecer com o estoque conforme a escolha", async () => {
    const user = userEvent.setup();
    const confirmar = vi.spyOn(window, "confirm").mockReturnValue(false);
    render(<VendaDetalhes venda={comLote} podeCancelar />);

    await user.type(screen.getByLabelText(/Motivo/), "Produto veio danificado");
    await user.click(screen.getByRole("radio", { name: /Descartar \(perda\)/ }));
    await user.click(screen.getByRole("button", { name: "Cancelar venda" }));
    expect(confirmar).toHaveBeenLastCalledWith(expect.stringContaining("5 unidade(s) serão registradas como perda nos lotes de origem"));

    await user.click(screen.getByRole("radio", { name: /Voltar ao estoque/ }));
    await user.click(screen.getByRole("button", { name: "Cancelar venda" }));
    expect(confirmar).toHaveBeenLastCalledWith(expect.stringContaining("voltarão aos lotes de origem"));
    expect(confirmar).toHaveBeenLastCalledWith(expect.stringContaining("O valor é estornado"));
  });

  it("venda sem lote cancela como antes, sem pergunta de destino", () => {
    const sem: Venda = { ...comLote, itens: [{ ...comLote.itens![0], lotes: [] }] };
    render(<VendaDetalhes venda={sem} podeCancelar />);
    expect(screen.queryByRole("radio")).toBeNull();
    expect(screen.getByRole("button", { name: "Cancelar venda" })).toBeDefined();
  });
});
