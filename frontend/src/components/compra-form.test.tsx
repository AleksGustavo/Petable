// @vitest-environment jsdom
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { FormState } from "@/lib/clientes";
import type { ProdutoRapido } from "@/lib/estoque";

const { criarCompra } = vi.hoisted(() => ({ criarCompra: vi.fn<(prev: unknown, fd: FormData) => Promise<FormState>>(async () => ({})) }));
vi.mock("@/app/actions/estoque", () => ({ criarCompra }));
vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { CompraForm } from "./compra-form";

const racao: ProdutoRapido = { id: 10, nome: "Ração Premium", codigoBarras: "7891234567890", precoVenda: 169.9, quantidadeEstoque: 3, disponivel: true };
const coleira: ProdutoRapido = { id: 12, nome: "Coleira", codigoBarras: null, precoVenda: 30, quantidadeEstoque: 0, disponivel: false };
const buscar = vi.fn(async () => [racao, coleira]);

afterEach(() => {
  criarCompra.mockClear();
  buscar.mockClear();
});

function renderizar(funcionarios: { id: number; usuarioId: number; nome: string }[] = []) {
  return render(
    <CompraForm
      fornecedores={[
        { id: 4, nomeFantasia: "Pet Distribuidora", cnpj: "11222333000181" },
        { id: 5, nomeFantasia: "Agro Pet", cnpj: "44555666000199" },
      ]}
      funcionarios={funcionarios}
      hoje="2026-09-25"
      buscarProdutos={buscar}
    />,
  );
}

async function adicionar(user: ReturnType<typeof userEvent.setup>, nome: string) {
  const busca = screen.getByLabelText("Adicionar produto");
  await user.clear(busca);
  await user.type(busca, "pr");
  await user.click(await screen.findByRole("button", { name: `Adicionar ${nome}` }));
}

describe("CompraForm", () => {
  it("adiciona e remove itens", async () => {
    const user = userEvent.setup();
    renderizar();
    expect(screen.getByText("Nenhum item adicionado.")).toBeDefined();

    await adicionar(user, "Ração Premium");
    await adicionar(user, "Coleira");
    const lista = screen.getByRole("list", { name: "Itens da compra" });
    expect(within(lista).getAllByRole("listitem")).toHaveLength(2);

    await user.click(screen.getByRole("button", { name: "Remover Ração Premium" }));
    expect(within(lista).getAllByRole("listitem")).toHaveLength(1);
    expect(within(lista).getByText("Coleira")).toBeDefined();
  });

  it("impede adicionar o mesmo produto duas vezes", async () => {
    const user = userEvent.setup();
    renderizar();
    await adicionar(user, "Ração Premium");

    await user.type(screen.getByLabelText("Adicionar produto"), "ra");
    const repetido = await screen.findByRole("button", { name: "Ração Premium já adicionado" });
    expect(repetido).toHaveProperty("disabled", true);
    expect(screen.getAllByText("Ração Premium")).toHaveLength(2); // linha do item + resultado da busca
  });

  it("calcula subtotal e total só para exibição", async () => {
    const user = userEvent.setup();
    renderizar();
    await adicionar(user, "Ração Premium");
    await adicionar(user, "Coleira");

    const qtdRacao = screen.getByLabelText("Quantidade", { selector: "#item-10-quantidade" });
    await user.clear(qtdRacao);
    await user.type(qtdRacao, "3");
    await user.type(screen.getByLabelText("Custo unit. (R$)", { selector: "#item-10-custo" }), "0,10");
    const qtdColeira = screen.getByLabelText("Quantidade", { selector: "#item-12-quantidade" });
    await user.clear(qtdColeira);
    await user.type(qtdColeira, "10");
    await user.type(screen.getByLabelText("Custo unit. (R$)", { selector: "#item-12-custo" }), "120,50");

    expect(screen.getByTestId("subtotal-10").textContent).toMatch(/0,30/);
    expect(screen.getByTestId("subtotal-12").textContent).toMatch(/1\.205,00/);
    expect(screen.getByTestId("total-compra").textContent).toMatch(/1\.205,30/);
  });

  it("não envia sem itens nem com custo inválido", async () => {
    const user = userEvent.setup();
    renderizar();
    await user.selectOptions(screen.getByLabelText(/Fornecedor/), "4");
    await user.type(screen.getByLabelText(/Forma de pagamento/), "Boleto");
    await user.click(screen.getByRole("button", { name: "Registrar pedido" }));
    expect(await screen.findByText("Adicione pelo menos um item")).toBeDefined();

    await adicionar(user, "Coleira");
    await user.click(screen.getByRole("button", { name: "Registrar pedido" }));
    expect(await screen.findByText("Informe um custo válido (até duas casas)")).toBeDefined();
    expect(criarCompra).not.toHaveBeenCalled();
  });

  it("mantém fornecedor e responsável escolhidos após erro da action", async () => {
    // Como a action real: devolve os valores para o formulário repopular após o reset do React 19.
    criarCompra.mockImplementationOnce(async (_prev, fd) => ({
      mensagem: "Fornecedor inativo.",
      valores: Object.fromEntries([...fd.entries()].map(([k, v]) => [k, String(v)])),
    }));
    const user = userEvent.setup();
    renderizar([{ id: 7, usuarioId: 3, nome: "Ana Compras" }]);
    await adicionar(user, "Ração Premium");
    await user.type(screen.getByLabelText("Custo unit. (R$)", { selector: "#item-10-custo" }), "12,50");
    await user.selectOptions(screen.getByLabelText(/Fornecedor/), "5");
    await user.selectOptions(screen.getByLabelText("Responsável"), "7");
    await user.type(screen.getByLabelText(/Forma de pagamento/), "Boleto");
    await user.click(screen.getByRole("button", { name: "Registrar pedido" }));

    expect(await screen.findByText("Fornecedor inativo.")).toBeDefined();
    expect(criarCompra).toHaveBeenCalled();
    expect(screen.getByLabelText(/Fornecedor/)).toHaveProperty("value", "5");
    expect(screen.getByLabelText("Responsável")).toHaveProperty("value", "7");
    expect(screen.getByLabelText(/Forma de pagamento/)).toHaveProperty("value", "Boleto");
  });
});
