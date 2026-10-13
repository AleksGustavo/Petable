// @vitest-environment jsdom
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ProdutoRapido } from "@/lib/estoque";
import { CHAVE_STORAGE, type PreviaVenda, type Venda } from "@/lib/vendas";

const { previaVenda, finalizarVenda } = vi.hoisted(() => ({ previaVenda: vi.fn(), finalizarVenda: vi.fn() }));
vi.mock("@/app/actions/vendas", () => ({ previaVenda, finalizarVenda }));
vi.mock("@/components/tutor-picker", () => ({ TutorPicker: () => <div>seletor de cliente</div> }));
vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { Pdv } from "./pdv";

const racao: ProdutoRapido = { id: 12, nome: "Ração Premium", codigoBarras: "7891234567890", precoVenda: 10, quantidadeEstoque: 5, disponivel: true };
const buscar = vi.fn(async () => [racao]);

const previaOk: PreviaVenda = {
  itens: [{ produtoId: 12, nome: "Ração Premium", precoUnitario: 10, quantidade: 1, subtotal: 10, disponivel: 5, suficiente: true }],
  subtotal: 10,
  desconto: 0,
  total: 10,
  troco: null,
  podeFinalizar: true,
};

const venda: Venda = {
  id: 91,
  dataVenda: "2026-09-26T11:03:11",
  status: "CONCLUIDA",
  cliente: null,
  funcionario: null,
  operador: { id: 5, nome: "joana" },
  itens: [],
  quantidadeItens: 1,
  subtotal: 10,
  desconto: 0,
  valorTotal: 10,
  formaPagamento: "PIX",
  valorRecebido: null,
  troco: null,
  observacoes: null,
  canceladaEm: null,
  canceladaPor: null,
  motivoCancelamento: null,
};

beforeEach(() => {
  sessionStorage.clear();
  vi.spyOn(window, "confirm").mockReturnValue(true);
  previaVenda.mockResolvedValue({ ok: true, previa: previaOk });
});

afterEach(() => {
  vi.restoreAllMocks();
  previaVenda.mockReset();
  finalizarVenda.mockReset();
});

async function montarCarrinho(user: ReturnType<typeof userEvent.setup>) {
  render(<Pdv papel="VENDAS" buscarProdutos={buscar} />);
  await user.type(screen.getByLabelText("Código de barras ou nome do produto"), "7891234567890{Enter}");
  await screen.findByRole("list", { name: "Itens do carrinho" });
  await user.click(screen.getByRole("radio", { name: "Pix" }));
}

describe("finalização no PDV", () => {
  it("confirma com o total do backend, conclui, limpa o carrinho e troca a chave", async () => {
    const user = userEvent.setup();
    finalizarVenda.mockResolvedValue({ ok: true, venda, repetida: false });
    await montarCarrinho(user);
    const chave = JSON.parse(sessionStorage.getItem(CHAVE_STORAGE)!).chave;

    await user.click(screen.getByRole("button", { name: "Finalizar venda" }));

    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining("Finalizar a venda de R$"));
    expect(finalizarVenda).toHaveBeenCalledWith(expect.objectContaining({ itens: [{ produtoId: 12, quantidade: 1 }], formaPagamento: "PIX" }), chave);
    expect(await screen.findByText("Venda #91 concluída")).toBeDefined();
    expect(sessionStorage.getItem(CHAVE_STORAGE)).toBeNull();

    // Nova venda usa outra chave.
    await user.click(screen.getByRole("button", { name: "Nova venda" }));
    await user.type(screen.getByLabelText("Código de barras ou nome do produto"), "7891234567890{Enter}");
    await screen.findByRole("list", { name: "Itens do carrinho" });
    expect(JSON.parse(sessionStorage.getItem(CHAVE_STORAGE)!).chave).not.toBe(chave);
  });

  it("não envia se o usuário cancelar a confirmação", async () => {
    const user = userEvent.setup();
    vi.mocked(window.confirm).mockReturnValue(false);
    await montarCarrinho(user);
    await user.click(screen.getByRole("button", { name: "Finalizar venda" }));
    expect(previaVenda).toHaveBeenCalled();
    expect(finalizarVenda).not.toHaveBeenCalled();
  });

  it("409 de estoque destaca a linha e mantém o carrinho", async () => {
    const user = userEvent.setup();
    finalizarVenda.mockResolvedValue({
      ok: false,
      status: 409,
      mensagem: "Estoque insuficiente para Ração Premium: disponível 0, solicitado 1.",
      produtoId: 12,
    });
    await montarCarrinho(user);

    await user.click(screen.getByRole("button", { name: "Finalizar venda" }));

    expect(await screen.findByText(/Estoque insuficiente para Ração Premium/)).toBeDefined();
    const linha = screen.getByTestId("linha-12");
    expect(linha.getAttribute("data-alerta")).toBe("true");
    expect(within(linha).getByText(/estoque insuficiente/)).toBeDefined();
    expect(sessionStorage.getItem(CHAVE_STORAGE)).not.toBeNull();
  });

  it("replay idempotente (200) mostra a mesma venda sem cobrar de novo", async () => {
    const user = userEvent.setup();
    finalizarVenda.mockResolvedValue({ ok: true, venda, repetida: true });
    await montarCarrinho(user);
    await user.click(screen.getByRole("button", { name: "Finalizar venda" }));
    expect(await screen.findByText("Venda #91 concluída")).toBeDefined();
    expect(screen.getByText(/já tinha sido registrada/)).toBeDefined();
  });

  it("preço alterado na prévia atualiza o carrinho e pede nova conferência", async () => {
    const user = userEvent.setup();
    previaVenda.mockResolvedValue({ ok: true, previa: { ...previaOk, itens: [{ ...previaOk.itens[0], precoUnitario: 12 }], subtotal: 12, total: 12 } });
    await montarCarrinho(user);
    await user.click(screen.getByRole("button", { name: "Finalizar venda" }));
    expect(await screen.findByText(/Preços atualizados pelo sistema/)).toBeDefined();
    expect(finalizarVenda).not.toHaveBeenCalled();
    expect(screen.getByTestId("subtotal-12").textContent).toMatch(/12,00/);
  });
});
