// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { Usuario } from "@/lib/usuarios";

vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { UsuariosLista } from "./usuarios-lista";

const ativo: Usuario = { id: 1, username: "maria", email: "maria@x.com", papel: "VENDAS", ativo: true, funcionario: { id: 7, nome: "Maria Silva" }, createdAt: "" };
const inativo: Usuario = { ...ativo, id: 2, username: "joao", email: "joao@x.com", papel: "SERVICO", ativo: false, funcionario: null };

describe("UsuariosLista", () => {
  it("mostra estado vazio com e sem filtro", () => {
    const { rerender } = render(<UsuariosLista usuarios={[]} />);
    expect(screen.getByText("Nenhum usuário cadastrado.")).toBeDefined();
    rerender(<UsuariosLista usuarios={[]} filtrado />);
    expect(screen.getByText("Nenhum usuário encontrado com os filtros selecionados.")).toBeDefined();
  });

  it("mostra papel, funcionário e situação em texto", () => {
    render(<UsuariosLista usuarios={[ativo, inativo]} />);
    expect(screen.getAllByText("Vendas").length).toBeGreaterThan(0);
    expect(screen.getAllByText(/Maria Silva/).length).toBeGreaterThan(0);
    expect(screen.getAllByText("Ativo").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Inativo").length).toBeGreaterThan(0);
    expect(screen.getAllByRole("link").map((l) => l.getAttribute("href"))).toEqual(expect.arrayContaining(["/usuarios/1", "/usuarios/2"]));
  });
});
