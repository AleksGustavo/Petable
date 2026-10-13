// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

const { solicitarRedefinicao } = vi.hoisted(() => ({
  solicitarRedefinicao: vi.fn(async () => ({
    enviado: true,
    mensagem: "Se existir uma conta ativa para o e-mail informado, enviaremos as instruções de redefinição.",
  })),
}));
vi.mock("@/app/actions/usuarios", () => ({ solicitarRedefinicao }));
vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { EsqueciSenhaForm } from "./esqueci-senha-form";

describe("EsqueciSenhaForm", () => {
  it("mostra sempre a mensagem neutra depois de enviar", async () => {
    const user = userEvent.setup();
    render(<EsqueciSenhaForm />);
    expect(screen.getByLabelText(/E-mail da conta/).getAttribute("autocomplete")).toBe("email");

    await user.type(screen.getByLabelText(/E-mail da conta/), "qualquer@x.com");
    await user.click(screen.getByRole("button", { name: "Enviar instruções" }));

    expect(await screen.findByText(/Se existir uma conta ativa/)).toBeDefined();
    expect(screen.queryByRole("button", { name: "Enviar instruções" })).toBeNull();
    expect(solicitarRedefinicao).toHaveBeenCalled();
  });
});
