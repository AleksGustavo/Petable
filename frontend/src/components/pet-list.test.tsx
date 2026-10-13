// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { Pet } from "@/lib/pets";

vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { PetList } from "./pet-list";

const pet: Pet = {
  id: 3,
  nome: "Thor",
  tutor: { id: 7, nome: "Mariana Costa", telefone: "19988761537" },
  especie: { id: 1, nome: "Cão" },
  raca: { id: 10, nome: "Labrador" },
  dataNascimento: "2021-04-10",
  idade: { anos: 5, meses: 5, descricao: "5 anos e 5 meses" },
  peso: 32.5,
  cor: null,
  porte: "GRANDE",
  castrado: true,
  fotoUrl: null,
  observacoes: null,
  createdAt: "2026-09-25T12:00:00Z",
  updatedAt: "2026-09-25T12:00:00Z",
};

describe("PetList", () => {
  it("mostra o estado vazio sem busca", () => {
    render(<PetList pets={[]} />);
    expect(screen.getByText("Nenhum pet cadastrado. Cadastre o primeiro pet para começar.")).toBeDefined();
  });

  it("mostra o estado vazio de busca", () => {
    render(<PetList pets={[]} busca="xyz" />);
    expect(screen.getByText("Nenhum pet encontrado para esta busca.")).toBeDefined();
  });

  it("lista pet e tutor com links para as fichas", () => {
    render(<PetList pets={[pet]} busca="thor" />);
    expect(screen.getAllByText("Thor").length).toBeGreaterThan(0);
    expect(screen.getAllByText(/Mariana Costa/).length).toBeGreaterThan(0);
    expect(screen.getAllByRole("link").map((a) => a.getAttribute("href"))).toEqual(
      expect.arrayContaining(["/pets/3", "/clientes/7"]),
    );
    expect(screen.getByText("5 anos e 5 meses")).toBeDefined();
  });
});
