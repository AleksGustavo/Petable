// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Usuario } from "@/lib/usuarios";

const { alterarPapelUsuario, alternarAtivoUsuario } = vi.hoisted(() => ({
  alterarPapelUsuario: vi.fn(async () => ({})),
  alternarAtivoUsuario: vi.fn(async () => ({ mensagem: "Não é possível remover ou desativar o último administrador ativo do sistema." })),
}));
vi.mock("@/app/actions/usuarios", () => ({ alterarPapelUsuario, alternarAtivoUsuario }));

import { UsuarioSeguranca } from "./usuario-seguranca";

const usuario: Usuario = { id: 5, username: "maria", email: "m@x.com", papel: "ADMIN", ativo: true, funcionario: null, createdAt: "" };

afterEach(() => {
  vi.restoreAllMocks();
  alternarAtivoUsuario.mockClear();
});

describe("UsuarioSeguranca", () => {
  it("mostra situação e papel em texto", () => {
    render(<UsuarioSeguranca usuario={usuario} ehVoceMesmo={false} />);
    expect(screen.getByText("Ativo")).toBeDefined();
    expect(screen.getByText("Administrador", { selector: "strong" })).toBeDefined();
  });

  it("não oferece alterar papel nem desativar a própria conta", () => {
    render(<UsuarioSeguranca usuario={usuario} ehVoceMesmo />);
    expect(screen.queryByLabelText("Papel")).toBeNull();
    expect(screen.queryByRole("button", { name: "Desativar usuário" })).toBeNull();
    expect(screen.getByText(/Esta é a sua conta/)).toBeDefined();
  });

  it("pede confirmação antes de desativar e não envia se cancelar", async () => {
    const user = userEvent.setup();
    const confirmar = vi.spyOn(window, "confirm").mockReturnValue(false);
    render(<UsuarioSeguranca usuario={usuario} ehVoceMesmo={false} />);

    await user.click(screen.getByRole("button", { name: "Desativar usuário" }));

    expect(confirmar).toHaveBeenCalledWith(expect.stringContaining("Desativar este usuário"));
    expect(alternarAtivoUsuario).not.toHaveBeenCalled();
  });

  it("mostra o erro do último ADMIN devolvido pela API", async () => {
    const user = userEvent.setup();
    vi.spyOn(window, "confirm").mockReturnValue(true);
    render(<UsuarioSeguranca usuario={usuario} ehVoceMesmo={false} />);

    await user.click(screen.getByRole("button", { name: "Desativar usuário" }));

    expect(await screen.findByText(/último administrador ativo/)).toBeDefined();
  });

  it("usuário inativo oferece reativar", () => {
    render(<UsuarioSeguranca usuario={{ ...usuario, ativo: false }} ehVoceMesmo={false} />);
    expect(screen.getByRole("button", { name: "Reativar usuário" })).toBeDefined();
  });
});
