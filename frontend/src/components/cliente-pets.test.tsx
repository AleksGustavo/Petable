// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { Pet } from "@/lib/pets";

vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { ClientePets } from "./cliente-pets";

const pet = {
  id: 3,
  nome: "Luna",
  tutor: { id: 7, nome: "Ana", telefone: "19988761537" },
  especie: { id: 2, nome: "Gato" },
  raca: null,
  idade: null,
  fotoUrl: null,
  updatedAt: "2026-09-25T12:00:00Z",
} as unknown as Pet;

describe("ClientePets", () => {
  it("lista os pets do cliente com link para a ficha", () => {
    render(<ClientePets clienteId={7} pets={[pet]} podeEditar={false} />);
    expect(screen.getByText("Pets (1)")).toBeDefined();
    expect(screen.getByRole("link", { name: /Luna/ }).getAttribute("href")).toBe("/pets/3");
    expect(screen.queryByText("Adicionar pet")).toBeNull();
  });

  it("oferece adicionar pet com o cliente pré-selecionado", () => {
    render(<ClientePets clienteId={7} pets={[]} podeEditar />);
    expect(screen.getByText("Nenhum pet cadastrado para este cliente.")).toBeDefined();
    expect(screen.getByRole("link", { name: "Adicionar pet" }).getAttribute("href")).toBe("/pets/novo?clienteId=7");
  });
});
