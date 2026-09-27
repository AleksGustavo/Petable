// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { Fornecedor } from "@/lib/estoque";

vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { FornecedoresLista } from "./fornecedores-lista";

const fornecedor: Fornecedor = {
  id: 4,
  nomeFantasia: "Pet Distribuidora",
  razaoSocial: "Pet Distribuidora Ltda",
  cnpj: "11222333000181",
  telefone: "1935000000",
  email: "contato@pet.com.br",
  contato: "Carlos",
  endereco: null,
  observacoes: null,
  ativo: true,
  createdAt: "",
  updatedAt: "",
};

describe("FornecedoresLista", () => {
  it("lista fornecedores com CNPJ formatado e situação", () => {
    render(<FornecedoresLista fornecedores={[fornecedor, { ...fornecedor, id: 5, nomeFantasia: "Antigo", ativo: false }]} papel="VENDAS" />);
    expect(screen.getAllByText("Pet Distribuidora").length).toBeGreaterThan(0);
    expect(screen.getAllByText(/11\.222\.333\/0001-81/).length).toBeGreaterThan(0);
    expect(screen.getAllByText("Inativo").length).toBeGreaterThan(0);
    expect(screen.getAllByRole("link").map((l) => l.getAttribute("href"))).toEqual(expect.arrayContaining(["/fornecedores/4", "/fornecedores/5"]));
  });

  it("mostra estado vazio", () => {
    render(<FornecedoresLista fornecedores={[]} papel="ADMIN" />);
    expect(screen.getByText("Nenhum fornecedor cadastrado.")).toBeDefined();
  });

  it("botão de novo fornecedor só para ADMIN", () => {
    const { rerender } = render(<FornecedoresLista fornecedores={[]} papel="VENDAS" />);
    expect(screen.queryByRole("link", { name: "+ Novo fornecedor" })).toBeNull();
    rerender(<FornecedoresLista fornecedores={[]} papel="ADMIN" />);
    expect(screen.getByRole("link", { name: "+ Novo fornecedor" }).getAttribute("href")).toBe("/fornecedores/novo");
  });
});
