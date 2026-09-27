// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { podeAnonimizarClientes } from "@/lib/permissoes";

const { anonimizarCliente } = vi.hoisted(() => ({
  anonimizarCliente: vi.fn(async () => ({ erro: "Digite ANONIMIZAR para confirmar." })),
}));
vi.mock("@/app/actions/clientes", () => ({ anonimizarCliente }));

import { ClientePrivacidade } from "./cliente-privacidade";

describe("privacidade do cliente (LGPD)", () => {
  it("oferece exportação e anonimização", () => {
    render(<ClientePrivacidade clienteId={7} />);
    expect(screen.getByRole("link", { name: "Exportar dados (JSON)" }).getAttribute("href")).toBe(
      "/api/clientes/7/dados-pessoais",
    );
    expect(screen.getByRole("button", { name: "Anonimizar definitivamente" })).toBeTruthy();
    expect(screen.getByText(/Não pode ser desfeito/)).toBeTruthy();
  });

  it("confirmação exige motivo e a palavra ANONIMIZAR; erro da action aparece", async () => {
    render(<ClientePrivacidade clienteId={7} />);
    await userEvent.click(screen.getByText("Anonimizar cliente"));
    const motivo = screen.getByLabelText("Motivo") as HTMLInputElement;
    expect(motivo.required).toBe(true);
    expect(motivo.minLength).toBe(5);
    await userEvent.type(motivo, "Pedido do titular");
    await userEvent.type(screen.getByLabelText("Digite ANONIMIZAR para confirmar"), "anonimizar");
    await userEvent.click(screen.getByRole("button", { name: "Anonimizar definitivamente" }));
    expect(anonimizarCliente).toHaveBeenCalledTimes(1);
    expect((await screen.findByRole("alert")).textContent).toContain("ANONIMIZAR");
  });

  it("só ADMIN vê o cartão", () => {
    expect(podeAnonimizarClientes("ADMIN")).toBe(true);
    expect(podeAnonimizarClientes("VENDAS")).toBe(false);
    expect(podeAnonimizarClientes("SERVICO")).toBe(false);
  });
});
