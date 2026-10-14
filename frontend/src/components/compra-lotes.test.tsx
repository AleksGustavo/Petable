// @vitest-environment jsdom
import { fireEvent, render, screen } from "@testing-library/react";
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
    { id: 1, produto: { id: 10, nome: "Vacina X" }, quantidade: 10, precoCustoUnitario: 26, subtotal: 260, controlaLote: true, exigeValidade: true, lotes: [] },
    { id: 2, produto: { id: 12, nome: "Coleira" }, quantidade: 5, precoCustoUnitario: 35, subtotal: 175, controlaLote: false, lotes: [] },
  ],
  createdAt: "",
};

afterEach(() => {
  vi.restoreAllMocks();
  receberCompra.mockClear();
});

async function preencher(user: ReturnType<typeof userEvent.setup>, codigo: string, validade: string, quantidade: string) {
  await user.type(screen.getByLabelText(/Código do lote 1/), codigo);
  await user.type(screen.getAllByLabelText(/^Validade/)[0], validade);
  await user.type(screen.getAllByLabelText(/^Quantidade/)[0], quantidade);
}

describe("recebimento de compra com lotes", () => {
  it("só o item de produto controlado ganha a grade; o outro segue como antes", () => {
    render(<CompraDetalhes compra={pedido} podeAdministrar hoje="2026-09-25" />);
    expect(screen.getAllByText(/Lotes recebidos de Vacina X \(10\)/).length).toBe(1);
    expect(screen.queryByText(/Lotes recebidos de Coleira/)).toBeNull();
    expect(screen.getAllByText(/Controla lote: o recebimento pedirá código e validade/).length).toBeGreaterThan(0);
  });

  it("receber nunca passa com lotes que não somam a quantidade do item", async () => {
    const user = userEvent.setup();
    const confirmar = vi.spyOn(window, "confirm").mockReturnValue(true);
    render(<CompraDetalhes compra={pedido} podeAdministrar hoje="2026-09-25" />);

    await preencher(user, "V-100", "2027-03-31", "7");
    expect(screen.getByRole("status").textContent).toMatch(/Faltam 3 para chegar a 10/);
    await user.click(screen.getByRole("button", { name: "Receber compra" }));

    expect(confirmar).not.toHaveBeenCalled();
    expect(receberCompra).not.toHaveBeenCalled();
    expect((await screen.findAllByText(/Faltam 3 para chegar a 10/)).length).toBeGreaterThan(0);
  });

  it("lote vencido não é aceito no recebimento", async () => {
    const user = userEvent.setup();
    vi.spyOn(window, "confirm").mockReturnValue(true);
    render(<CompraDetalhes compra={pedido} podeAdministrar hoje="2026-09-25" />);

    await preencher(user, "V-OLD", "2026-09-01", "10");
    // O campo de data tem min=hoje (o navegador já avisa); a regra também é conferida no envio, que é o que se testa aqui.
    fireEvent.submit(screen.getByRole("button", { name: "Receber compra" }).closest("form") as HTMLFormElement);
    expect(await screen.findByText("Lote já vencido não pode ser recebido")).toBeDefined();
    expect(receberCompra).not.toHaveBeenCalled();
  });

  it("vários lotes na mesma linha: soma exata libera a confirmação e leva as grades no formulário", async () => {
    const user = userEvent.setup();
    const confirmar = vi.spyOn(window, "confirm").mockReturnValue(false);
    const { container } = render(<CompraDetalhes compra={pedido} podeAdministrar hoje="2026-09-25" />);

    await preencher(user, "V-100", "2027-03-31", "6");
    await user.click(screen.getByRole("button", { name: "+ Adicionar lote" }));
    await user.type(screen.getByLabelText(/Código do lote 2/), "V-101");
    await user.type(screen.getAllByLabelText(/^Validade/)[1], "2027-05-31");
    await user.type(screen.getAllByLabelText(/^Quantidade/)[1], "4");
    expect(screen.getByRole("status").textContent).toMatch(/confere/);
    await user.click(screen.getByRole("button", { name: "Receber compra" }));

    expect(confirmar).toHaveBeenCalled();
    const lotes = JSON.parse((container.querySelector('input[name="lotes"]') as HTMLInputElement).value);
    expect(lotes).toEqual([
      { produtoId: 10, linhas: [{ codigo: "V-100", validade: "2027-03-31", quantidade: "6" }, { codigo: "V-101", validade: "2027-05-31", quantidade: "4" }] },
    ]);
    expect((container.querySelector('input[name="quantidades"]') as HTMLInputElement).value).toBe("10:10:1");
  });

  it("compra sem produto controlado não ganha grade nem campos ocultos", () => {
    const comum: Compra = { ...pedido, itens: [pedido.itens![1]] };
    const { container } = render(<CompraDetalhes compra={comum} podeAdministrar hoje="2026-09-25" />);
    expect(container.querySelector('input[name="lotes"]')).toBeNull();
    expect(screen.queryByText(/Lotes recebidos de/)).toBeNull();
  });

  it("compra recebida mostra os lotes de cada item com validade em DD/MM/AAAA", () => {
    const recebida: Compra = {
      ...pedido,
      status: "RECEBIDO",
      dataEntrega: "2026-09-25",
      itens: [{ ...pedido.itens![0], lotes: [{ loteId: 1, codigoLote: "V-100", validade: "2027-03-31", quantidade: 10 }] }],
    };
    render(<CompraDetalhes compra={recebida} podeAdministrar hoje="2026-09-25" />);
    expect(screen.getAllByText(/V-100 \(val\. 31\/03\/2027\) × 10/).length).toBeGreaterThan(0);
    expect(screen.queryByRole("button", { name: "Receber compra" })).toBeNull();
  });
});
