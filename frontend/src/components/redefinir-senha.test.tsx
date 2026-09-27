// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

const { redefinirSenha } = vi.hoisted(() => ({ redefinirSenha: vi.fn() }));
vi.mock("@/app/actions/usuarios", () => ({ redefinirSenha }));
vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { RedefinirSenhaForm } from "./redefinir-senha-form";

async function enviar() {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText(/^Nova senha/), "Nova@Senha2026");
  await user.type(screen.getByLabelText(/Confirme a nova senha/), "Nova@Senha2026");
  await user.click(screen.getByRole("button", { name: "Redefinir senha" }));
}

describe("RedefinirSenhaForm", () => {
  it("token ausente mostra link inválido e oferece pedir outro", () => {
    render(<RedefinirSenhaForm token={null} />);
    expect(screen.getByText("O link de redefinição é inválido ou está incompleto.")).toBeDefined();
    expect(screen.getByRole("link", { name: "Pedir um novo link" }).getAttribute("href")).toBe("/esqueci-senha");
  });

  it("não exibe o token na tela", () => {
    const { container } = render(<RedefinirSenhaForm token="segredo-abc" />);
    expect(container.textContent).not.toContain("segredo-abc");
    expect(container.querySelector('input[name="token"]')?.getAttribute("type")).toBe("hidden");
  });

  it("token inválido mostra o erro da API", async () => {
    redefinirSenha.mockResolvedValueOnce({ erros: { token: "O link de redefinição é inválido ou expirou." }, mensagem: "O link de redefinição é inválido ou expirou." });
    render(<RedefinirSenhaForm token="expirado" />);
    await enviar();
    expect(await screen.findByText("O link de redefinição é inválido ou expirou.")).toBeDefined();
  });

  it("sucesso leva ao login", async () => {
    redefinirSenha.mockResolvedValueOnce({ sucesso: true });
    render(<RedefinirSenhaForm token="valido" />);
    await enviar();
    expect((await screen.findByRole("link", { name: "Ir para o login" })).getAttribute("href")).toBe("/login?aviso=senha-redefinida");
  });
});
