// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { FormState } from "@/lib/clientes";
import type { Movimentacao, Produto } from "@/lib/estoque";
import type { Lote } from "@/lib/lotes";

const { ajustarEstoque, alternarAtivoProduto } = vi.hoisted(() => ({
  ajustarEstoque: vi.fn<(id: unknown, prev: unknown, fd: FormData) => Promise<FormState>>(async () => ({})),
  alternarAtivoProduto: vi.fn(async () => ({})),
}));
vi.mock("@/app/actions/estoque", () => ({ ajustarEstoque, alternarAtivoProduto }));
vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { AjusteEstoqueForm, HistoricoEstoque, ProdutoAcoes, ProdutoDados } from "./produto-detalhes";

const produto: Produto = {
  id: 10,
  nome: "Ração Premium",
  descricao: "Seca, 10kg",
  categoria: { id: 1, nome: "Ração" },
  fornecedorPadrao: { id: 4, nome: "Pet Distribuidora" },
  codigoBarras: "7891234567890",
  precoCusto: 22,
  precoVenda: 169.9,
  quantidadeEstoque: 10,
  estoqueMinimo: 5,
  abaixoDoMinimo: false,
  ativo: true,
  createdAt: "",
  updatedAt: "",
};

const movimentacoes: Movimentacao[] = [
  { id: 2, produtoId: 10, tipo: "AJUSTE", quantidade: -3, saldoAnterior: 13, saldoResultante: 10, compraId: null, vendaId: null, usuario: { id: 1, nome: "admin" }, motivo: "Avaria", createdAt: "2026-09-25T13:00:00Z" },
  { id: 1, produtoId: 10, tipo: "ENTRADA_COMPRA", quantidade: 13, saldoAnterior: 0, saldoResultante: 13, compraId: 50, vendaId: null, usuario: { id: 1, nome: "admin" }, motivo: null, createdAt: "2026-09-24T13:00:00Z" },
];

afterEach(() => {
  vi.restoreAllMocks();
  ajustarEstoque.mockClear();
  alternarAtivoProduto.mockClear();
});

describe("detalhes do produto", () => {
  it("mostra os dados do produto", () => {
    render(<ProdutoDados produto={produto} />);
    expect(screen.getByText("Pet Distribuidora")).toBeDefined();
    expect(screen.getByText("7891234567890")).toBeDefined();
    expect(screen.getByText(/22,00/)).toBeDefined();
    expect(screen.getByText("Seca, 10kg")).toBeDefined();
  });

  it("mostra o histórico com tipo, sinal, saldos e origem", () => {
    render(<HistoricoEstoque movimentacoes={movimentacoes} />);
    expect(screen.getAllByText("Ajuste manual").length).toBeGreaterThan(0);
    expect(screen.getAllByText("−3").length).toBeGreaterThan(0);
    expect(screen.getAllByText("+13").length).toBeGreaterThan(0);
    expect(screen.getAllByText("13 → 10").length).toBeGreaterThan(0);
    expect(screen.getAllByRole("link", { name: "Compra #50" })[0].getAttribute("href")).toBe("/compras/50");
    // Instante UTC exibido no fuso de São Paulo.
    expect(screen.getAllByText("25/09/2026 10:00").length).toBeGreaterThan(0);
  });

  it("mostra estado vazio do histórico", () => {
    render(<HistoricoEstoque movimentacoes={[]} />);
    expect(screen.getByText("Nenhuma movimentação de estoque registrada.")).toBeDefined();
  });

  it("ajuste pede confirmação com o saldo resultante e bloqueia saldo negativo", async () => {
    const user = userEvent.setup();
    const confirmar = vi.spyOn(window, "confirm").mockReturnValue(false);
    render(<AjusteEstoqueForm produto={produto} />);

    await user.type(screen.getByLabelText(/Quantidade/), "-11");
    await user.type(screen.getByLabelText(/Motivo/), "Baixa por avaria");
    await user.click(screen.getByRole("button", { name: "Ajustar estoque" }));
    expect(await screen.findByText(/não pode deixá-lo negativo/)).toBeDefined();
    expect(confirmar).not.toHaveBeenCalled();

    await user.clear(screen.getByLabelText(/Quantidade/));
    await user.type(screen.getByLabelText(/Quantidade/), "-4");
    await user.click(screen.getByRole("button", { name: "Ajustar estoque" }));
    expect(confirmar).toHaveBeenCalledWith(expect.stringContaining("de 10 para 6"));
    expect(ajustarEstoque).not.toHaveBeenCalled();
  });

  it("desativação pede confirmação e não envia se cancelar", async () => {
    const user = userEvent.setup();
    const confirmar = vi.spyOn(window, "confirm").mockReturnValue(false);
    render(<ProdutoAcoes produto={produto} podeDesativar podeReativar={false} />);

    await user.click(screen.getByRole("button", { name: "Desativar produto" }));

    expect(confirmar).toHaveBeenCalledWith(expect.stringContaining("saldo (10) e o histórico são preservados"));
    expect(alternarAtivoProduto).not.toHaveBeenCalled();
  });

  it("reativar só aparece para quem pode reativar", () => {
    const inativo = { ...produto, ativo: false };
    const { rerender } = render(<ProdutoAcoes produto={inativo} podeDesativar podeReativar={false} />);
    expect(screen.queryByRole("button", { name: "Reativar produto" })).toBeNull();
    rerender(<ProdutoAcoes produto={inativo} podeDesativar podeReativar />);
    expect(screen.getByRole("button", { name: "Reativar produto" })).toBeDefined();
  });

  it("ajuste com lote mantém o lote escolhido após erro da action", async () => {
    // Como a action real: devolve os valores para o formulário repopular após o reset do React 19.
    ajustarEstoque.mockImplementationOnce(async (_id, _prev, fd) => ({ mensagem: "Saldo do lote insuficiente.", valores: Object.fromEntries([...fd.entries()].map(([k, v]) => [k, String(v)])) }));
    vi.spyOn(window, "confirm").mockReturnValue(true);
    const user = userEvent.setup();
    const lotes = [
      { id: 1, codigoLote: "L-001", saldo: 4 },
      { id: 2, codigoLote: "L-002", saldo: 6 },
    ] as Lote[];
    render(<AjusteEstoqueForm produto={{ ...produto, controlaLote: true }} lotes={lotes} />);

    await user.selectOptions(screen.getByLabelText(/^Lote/), "2");
    await user.type(screen.getByLabelText(/Quantidade/), "-3");
    await user.type(screen.getByLabelText(/Motivo/), "Baixa por avaria");
    await user.click(screen.getByRole("button", { name: "Ajustar estoque" }));

    expect(await screen.findByText("Saldo do lote insuficiente.")).toBeDefined();
    expect(ajustarEstoque).toHaveBeenCalled();
    expect(screen.getByLabelText(/^Lote/)).toHaveProperty("value", "2");
  });
});
