// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

// Como a action real: devolve os valores para o formulário repopular após o reset do React 19.
const { salvarProduto } = vi.hoisted(() => ({
  salvarProduto: vi.fn(async (_id: unknown, _prev: unknown, fd: FormData) => ({
    mensagem: "Já existe um produto com este código de barras.",
    valores: Object.fromEntries([...fd.entries()].map(([k, v]) => [k, String(v)])),
  })),
}));
vi.mock("@/app/actions/estoque", () => ({ salvarProduto }));
vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { ProdutoForm } from "./produto-form";

const categorias = [
  { id: 1, nome: "Rações", ativo: true },
  { id: 2, nome: "Acessórios", ativo: true },
];
const fornecedores = [
  { id: 4, nomeFantasia: "Pet Distribuidora", cnpj: "11222333000181" },
  { id: 5, nomeFantasia: "Agro Pet", cnpj: "44555666000199" },
];

describe("ProdutoForm", () => {
  it("mantém categoria e fornecedor padrão escolhidos após erro da action", async () => {
    const user = userEvent.setup();
    render(<ProdutoForm categorias={categorias as never} fornecedores={fornecedores} />);
    await user.type(screen.getByLabelText(/Nome/), "Ração Premium");
    await user.selectOptions(screen.getByLabelText(/Categoria/), "2");
    await user.selectOptions(screen.getByLabelText("Fornecedor padrão"), "5");
    await user.type(screen.getByLabelText(/Preço de venda/), "10,00");
    await user.click(screen.getByRole("button", { name: "Cadastrar produto" }));

    expect(await screen.findByText("Já existe um produto com este código de barras.")).toBeDefined();
    expect(salvarProduto).toHaveBeenCalled();
    expect(screen.getByLabelText(/Categoria/)).toHaveProperty("value", "2");
    expect(screen.getByLabelText("Fornecedor padrão")).toHaveProperty("value", "5");
    expect(screen.getByLabelText(/Nome/)).toHaveProperty("value", "Ração Premium");
  });
});
