// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";

const { abrirCaixa } = vi.hoisted(() => ({
  abrirCaixa: vi.fn(async () => ({ mensagem: "Já existe um caixa aberto para o usuário atual." })),
}));
vi.mock("@/app/actions/financeiro", () => ({ abrirCaixa, fecharCaixa: vi.fn(), movimentarCaixa: vi.fn() }));
vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { CaixaAbertura } from "./caixa";

afterEach(() => abrirCaixa.mockClear());

describe("CaixaAbertura", () => {
  it("envia o fundo de troco informado", async () => {
    const user = userEvent.setup();
    render(<CaixaAbertura />);
    await user.type(screen.getByLabelText(/Fundo de troco/), "200,00");
    await user.click(screen.getByRole("button", { name: "Abrir caixa" }));

    expect(abrirCaixa).toHaveBeenCalled();
    const formData = (abrirCaixa.mock.calls[0] as unknown[]).at(-1) as FormData;
    expect(formData.get("fundoAbertura")).toBe("200,00");
  });

  it("mostra o 409 de caixa já aberto", async () => {
    const user = userEvent.setup();
    render(<CaixaAbertura />);
    await user.click(screen.getByRole("button", { name: "Abrir caixa" }));
    expect(await screen.findByText("Já existe um caixa aberto para o usuário atual.")).toBeDefined();
  });
});
