// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ProdutoRapido } from "@/lib/estoque";
import { CHAVE_STORAGE } from "@/lib/vendas";

const { previaVenda, finalizarVenda } = vi.hoisted(() => ({ previaVenda: vi.fn(), finalizarVenda: vi.fn() }));
vi.mock("@/app/actions/vendas", () => ({ previaVenda, finalizarVenda }));
vi.mock("@/components/tutor-picker", () => ({ TutorPicker: () => <div>seletor de cliente</div> }));
vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { Pdv } from "./pdv";

const racao: ProdutoRapido = { id: 12, nome: "Ração Premium", codigoBarras: "7891234567890", precoVenda: 10, quantidadeEstoque: 5, disponivel: true };
const buscar = vi.fn(async () => [racao]);

beforeEach(() => {
  sessionStorage.clear();
  vi.spyOn(window, "confirm").mockReturnValue(true);
  previaVenda.mockResolvedValue({
    ok: true,
    previa: { itens: [{ produtoId: 12, nome: "Ração Premium", precoUnitario: 10, quantidade: 1, subtotal: 10, disponivel: 5, suficiente: true }], subtotal: 10, desconto: 0, total: 10, troco: null, podeFinalizar: true },
  });
});

afterEach(() => {
  vi.restoreAllMocks();
  previaVenda.mockReset();
  finalizarVenda.mockReset();
});

describe("PDV x caixa", () => {
  it("sem caixa aberto avisa e bloqueia a finalização", async () => {
    const user = userEvent.setup();
    render(<Pdv papel="VENDAS" buscarProdutos={buscar} caixaAberto={false} />);
    expect(screen.getByText("Abra o caixa para realizar vendas.")).toBeDefined();
    expect(screen.getByRole("link", { name: "Abrir caixa" }).getAttribute("href")).toBe("/caixa");

    await user.type(screen.getByLabelText("Código de barras ou nome do produto"), "7891234567890{Enter}");
    await user.click(screen.getByRole("radio", { name: "Pix" }));
    expect(screen.getByRole("button", { name: "Finalizar venda" })).toHaveProperty("disabled", true);
    expect(finalizarVenda).not.toHaveBeenCalled();
  });

  it("caixa fechado em outra aba (409): bloqueia e preserva o carrinho", async () => {
    const user = userEvent.setup();
    finalizarVenda.mockResolvedValue({ ok: false, status: 409, mensagem: "Não há caixa aberto para o operador atual. Abra o caixa antes de continuar." });
    render(<Pdv papel="VENDAS" buscarProdutos={buscar} caixaAberto />);
    await user.type(screen.getByLabelText("Código de barras ou nome do produto"), "7891234567890{Enter}");
    await screen.findByRole("list", { name: "Itens do carrinho" });
    await user.click(screen.getByRole("radio", { name: "Pix" }));

    await user.click(screen.getByRole("button", { name: "Finalizar venda" }));

    expect(await screen.findByText("Abra o caixa para realizar vendas.")).toBeDefined();
    expect(screen.getByRole("list", { name: "Itens do carrinho" })).toBeDefined();
    expect(JSON.parse(sessionStorage.getItem(CHAVE_STORAGE)!).itens).toHaveLength(1);
    expect(screen.getByRole("button", { name: "Finalizar venda" })).toHaveProperty("disabled", true);
  });
});
