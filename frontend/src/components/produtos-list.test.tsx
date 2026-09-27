// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { Produto } from "@/lib/estoque";

vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { ProdutosFiltros, ProdutosLista } from "./produtos-lista";

const racao: Produto = {
  id: 1,
  nome: "Ração Premium",
  descricao: null,
  categoria: { id: 1, nome: "Ração" },
  fornecedorPadrao: null,
  codigoBarras: "7891234567890",
  precoCusto: 20,
  precoVenda: 169.9,
  quantidadeEstoque: 3,
  estoqueMinimo: 5,
  abaixoDoMinimo: true,
  ativo: true,
  createdAt: "",
  updatedAt: "",
};
const coleira: Produto = { ...racao, id: 2, nome: "Coleira", codigoBarras: null, quantidadeEstoque: 10, abaixoDoMinimo: false, ativo: false };

describe("ProdutosLista", () => {
  it("renderiza produtos com preço, saldo e links", () => {
    render(<ProdutosLista produtos={[racao, coleira]} />);
    expect(screen.getAllByText("Ração Premium").length).toBeGreaterThan(0);
    expect(screen.getAllByText(/169,90/).length).toBeGreaterThan(0);
    expect(screen.getAllByRole("link").map((l) => l.getAttribute("href"))).toEqual(expect.arrayContaining(["/produtos/1", "/produtos/2"]));
  });

  it("mostra os badges de estoque em texto", () => {
    render(<ProdutosLista produtos={[racao, coleira]} />);
    expect(screen.getAllByText("⚠ Abaixo do mínimo").length).toBeGreaterThan(0);
    expect(screen.getAllByText("✓ Estoque adequado").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Inativo").length).toBeGreaterThan(0);
  });

  it("mostra estado vazio com e sem filtro", () => {
    const { rerender } = render(<ProdutosLista produtos={[]} />);
    expect(screen.getByText("Nenhum produto cadastrado.")).toBeDefined();
    rerender(<ProdutosLista produtos={[]} filtrado />);
    expect(screen.getByText("Nenhum produto encontrado com os filtros selecionados.")).toBeDefined();
  });

  it("formulário de busca mantém os filtros atuais", () => {
    render(
      <ProdutosFiltros
        filtros={{ q: "ração", categoriaId: "2", ativo: "true", abaixoMinimo: true }}
        categorias={[{ id: 1, nome: "Acessório", descricao: null }, { id: 2, nome: "Ração", descricao: null }]}
      />,
    );
    expect(screen.getByRole("search")).toBeDefined();
    expect(screen.getByLabelText("Buscar por nome ou código de barras")).toHaveProperty("value", "ração");
    expect(screen.getByLabelText("Categoria")).toHaveProperty("value", "2");
    expect(screen.getByLabelText("Abaixo do mínimo")).toHaveProperty("checked", true);
  });
});
