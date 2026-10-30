// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";

// Como a action real: devolve os valores para o formulário repopular após o reset do React 19.
const { salvarFornecedor } = vi.hoisted(() => ({
  salvarFornecedor: vi.fn(async (_id: unknown, _prev: unknown, fd: FormData) => ({
    mensagem: "Já existe um fornecedor com este CNPJ.",
    valores: Object.fromEntries([...fd.entries()].map(([k, v]) => [k, String(v)])),
  })),
}));
vi.mock("@/app/actions/estoque", () => ({ salvarFornecedor }));
vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { FornecedorForm } from "./fornecedor-form";

afterEach(() => vi.unstubAllGlobals());

describe("FornecedorForm", () => {
  it("mantém a UF escolhida após erro da action", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => Promise.reject(new Error("sem rede")))); // ViaCEP fora: segue manual
    const user = userEvent.setup();
    render(<FornecedorForm />);
    await user.type(screen.getByLabelText(/Nome fantasia/), "Pet Distribuidora");
    await user.type(screen.getByLabelText(/Razão social/), "Pet Distribuidora Ltda");
    await user.type(screen.getByLabelText(/CNPJ/), "11222333000181");
    await user.type(screen.getByLabelText(/CEP/), "13560000");
    await user.type(screen.getByLabelText(/Logradouro/), "Rua A");
    await user.type(screen.getByLabelText(/Cidade/), "São Carlos");
    await user.selectOptions(screen.getByLabelText(/UF/), "SP");
    await user.click(screen.getByRole("button", { name: "Cadastrar fornecedor" }));

    expect(await screen.findByText("Já existe um fornecedor com este CNPJ.")).toBeDefined();
    expect(salvarFornecedor).toHaveBeenCalled();
    expect(screen.getByLabelText(/UF/)).toHaveProperty("value", "SP");
    expect(screen.getByLabelText(/Cidade/)).toHaveProperty("value", "São Carlos");
  });
});
